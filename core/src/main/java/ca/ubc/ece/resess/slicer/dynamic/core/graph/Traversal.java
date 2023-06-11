package ca.ubc.ece.resess.slicer.dynamic.core.graph;

import java.util.*;


import ca.ubc.ece.resess.slicer.dynamic.core.accesspath.AccessPath;
import ca.ubc.ece.resess.slicer.dynamic.core.accesspath.AliasSet;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.LazyStatementMap;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementInstance;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementMap;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisCache;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisUtils;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.IdentityStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.ReturnStmt;
import soot.jimple.ReturnVoidStmt;
import soot.jimple.Stmt;
import soot.toolkits.scalar.Pair;

public class Traversal {

    private DynamicControlFlowGraph icdg;
    private AnalysisCache analysisCache;
    public Traversal(DynamicControlFlowGraph icdg, AnalysisCache analysisCache) {
        this.icdg = icdg;
        this.analysisCache = analysisCache;
    }

    public Set<StatementInstance> previousNodes(StatementInstance iu) {
        Set<StatementInstance> previous = new LinkedHashSet<>();
        List<Integer> nodes = icdg.predecessorListOf(iu.getLineNo());
        for (Integer node: nodes) {
            StatementInstance next = icdg.mapNoUnits(node);
            if (next != null) {
                previous.add(next);
            }
        }
        return previous;
    }

    public Set<StatementInstance> nextNodes(StatementInstance iu) {
        Set<StatementInstance> following = new LinkedHashSet<>();
        List<Integer> nodes = icdg.successorListOf(iu.getLineNo());
        for (Integer node: nodes) {
            StatementInstance next = icdg.mapNoUnits(node);
            if (next != null) {
                following.add(next);
            }
        }
        return following;
    }

    public StatementMap getChunk(StatementInstance iu) {
        StatementMap chunk;
        if (iu.isReturn()) {
            chunk = iu.getReturnChunk();
            if (chunk == null) {
                chunk = getChunk(iu.getLineNo());
            }
        } else {
            chunk = getChunk(iu.getLineNo());
        }
        return chunk;
    }

    public StatementMap getChunk(int pos) {
        int startPos = pos;
        StatementMap cachedChunk = analysisCache.getFromBwChunkCache(pos);
        if (cachedChunk != null) {
            return cachedChunk;
        }
        StatementInstance iu = icdg.mapNoUnits(pos);
        if (iu == null) {
            return null;
        }
        String currentMethod = iu.getMethod().getSignature();
        StatementMap chunk = new StatementMap();
        boolean done = false;
        int newPos = 0;
        while(pos>=0 && !done) {
            iu = icdg.mapNoUnits(pos);
            if (iu!=null) {
                if(iu.getMethod().getSignature().equals(currentMethod)) {
                    chunk.put(iu.getUnitId(), iu);
                } else {
                    done = true;
                }
            }
            newPos = previousFlowEdge(pos);
            if (newPos != pos) {
                pos = newPos;
            } else {
                done = true;
            }
        }
        analysisCache.putInBwChunkCache(startPos, chunk);
        return chunk;
    }

    public LazyStatementMap getLazyChunk(StatementInstance iu) {
        return getLazyChunk(iu.getLineNo());
    }

    public LazyStatementMap getLazyChunk(int pos) {
        LazyStatementMap cachedChunk = analysisCache.getFromLazyChunkCache( pos );
        if (cachedChunk != null) {
            return cachedChunk;
        }
        StatementInstance iu = icdg.mapNoUnits( pos );
        if (iu == null) {
            return null;
        }
        LazyStatementMap lazyChunk = new LazyStatementMap( iu, icdg, false, this::previousFlowEdge, analysisCache );
        return lazyChunk;
    }

    private int previousFlowEdge( int pos ) {
        int newPos = pos;
        List<Integer> preds = icdg.predecessorListOf(pos);
        for (Integer pred: preds) {
            Edge e = icdg.getEdge(pred, pos);
            if (e.getEdgeType().equals(EdgeType.FLOW_EDGE)) {
                newPos = pred;
            }
        }
        return newPos;
    }

    public Pair<CalledChunk, AccessPath> getReturnIfStmtIsCall(int pos) {
        if (!icdg.mapNoUnits(pos).containsInvokeExpr()) {
            return null;
        }
        CalledChunk calledChunk = getCalledChunk(pos);
        if (calledChunk.getRetIu() != null){
            if (((Stmt) icdg.mapNoUnits(pos).getUnit()).getInvokeExpr().getMethod().getName().equals(calledChunk.getRetIu().getMethod().getName())) {
                if (calledChunk.getRetVariable() == null) {
                    InvokeExpr invokeExpr = AnalysisUtils.getCallerExp(icdg.mapNoUnits(pos));
                    if (invokeExpr != null && !invokeExpr.getMethod().isStatic()) {
                        calledChunk.setRetVariable("r0");
                        calledChunk.setRetVarType(invokeExpr.getMethod().getDeclaringClass().getType());
                    }
                }
                if (calledChunk.getRetVariable() != null && calledChunk.getChunk() != null) {
                    return new Pair<>(calledChunk, new AccessPath(calledChunk.getRetVariable(), calledChunk.getRetVarType(), calledChunk.getRetIu().getLineNo(), AccessPath.NOT_DEFINED, calledChunk.getRetIu()));
                }
            }
        }
        return null;
    }

    public LazyStatementMap getForwardLazyChunk(int pos) {
        StatementInstance iu = icdg.mapNoUnits( pos );
        if (iu == null) {
            return null;
        }
        LazyStatementMap lazyChunk = new LazyStatementMap( iu, icdg, true, this::nextFlowEdge, analysisCache );
        return lazyChunk;
    }

    public StatementMap getForwardChunk(int pos) {
        int startPos = pos;
        StatementMap cachedChunk = analysisCache.getFromFwChunkCache(pos);
        if (cachedChunk != null) {
            return cachedChunk;
        }
        StatementMap chunk = new StatementMap();
        StatementInstance iu = icdg.mapNoUnits(pos);
        int newPos = 0;
        String currentMethod = iu.getMethod().getSignature();
        while(pos>=0) {
            iu = icdg.mapNoUnits(pos);
            if (iu!=null) {
                if(iu.getMethod().getSignature().equals(currentMethod)) {
                    chunk.put(iu.getUnitId(), iu);
                } else {
                    break;
                }
            }
            newPos = nextFlowEdge(pos);
            if (newPos != pos) {
                pos = newPos;
            } else {
                break;
            }
        }
        analysisCache.putInFwChunkCache(startPos, chunk);
        return chunk;
    }

    public int nextFlowEdge(int pos) {
        int newPos = pos;
        List<Integer> successors = icdg.successorListOf(pos);
        Collections.sort(successors);
        for (Integer s: successors) {
            Edge e = icdg.getEdge(pos, s);
            if (e.getEdgeType().equals(EdgeType.FLOW_EDGE)) {
                newPos = s;
            }
        }
        return newPos;
    }

    public int nextCallOrFlowEdge(int pos) {
        int newPos = pos;
        List<Integer> successors = icdg.successorListOf(pos);
        Collections.sort(successors);
        for (Integer s: successors) {
            Edge e = icdg.getEdge(pos, s);
            if (e.getEdgeType().equals(EdgeType.CALL_EDGE)) {
                newPos = s;
                break;
            }
            if (e.getEdgeType().equals(EdgeType.FLOW_EDGE)) {
                newPos = s;
            }
        }
        return newPos;
    }

    public CalledChunk getCalledChunk(int pos) {
        StatementInstance start = icdg.mapNoUnits(pos);
        CalledChunk cached = analysisCache.getFromCalledChunkCache(start);
        if (cached != null) {
            return cached;
        }
        CalledChunk calledChunk = new CalledChunk();
        int newPos = 0;
        boolean foundBody;
        Pair<Integer, Boolean> searchResult = searchForMethod(pos);
        pos = searchResult.getO1();
        foundBody = searchResult.getO2();
        if (icdg.mapNoUnits(pos) == null || !foundBody) {
            calledChunk.setChunk(null);
            analysisCache.putInCalledChunkCache(start, calledChunk);
            return calledChunk;
        }
        StatementInstance iu = icdg.mapNoUnits(pos);
        calledChunk.setRetLine(pos);
        calledChunk.setRetIu(iu);
        String currentMethod = iu.getMethod().getSignature();
        while(pos>=0) {
            iu = icdg.mapNoUnits(pos);
            if (iu!=null) {
                if(iu.getMethod().getSignature().equals(currentMethod)) {
                    calledChunk.getChunk().put(iu.getUnitId(), iu);
                } else {
                    break;
                }
            }
            newPos = nextFlowEdge(pos);
            if (newPos != pos) {
                pos = newPos;
            } else {
                break;
            }
        }

        if (iu == null) {
            calledChunk.setChunk(null);
            analysisCache.putInCalledChunkCache(start, calledChunk);
            return calledChunk;
        }

        addReturnVariable(iu, calledChunk);

        analysisCache.putInCalledChunkCache(start, calledChunk);
        return calledChunk;
    }


    public Pair<Integer, Boolean> searchForMethod(int pos) {
        Pair<Integer, Boolean> searchResult = new Pair<>();
        List<Integer> successors = icdg.successorListOf(pos);
        searchResult.setO1(pos);
        searchResult.setO2(false);
        for (Integer s: successors) {
            Edge e = icdg.getEdge(pos, s);
            if (e.getEdgeType().equals(EdgeType.CALL_EDGE)) {
                searchResult.setO1(s);
                searchResult.setO2(true);
                break;
            }
        }
        return searchResult;
    }




    private void addReturnVariable(StatementInstance iu, CalledChunk calledChunk) {
        if (iu.getUnit() instanceof ReturnStmt || iu.getUnit() instanceof ReturnVoidStmt) {
            calledChunk.setRetIu(iu);
            if (iu.getUnit() instanceof ReturnStmt) {
                calledChunk.setRetVariable(iu.getReturnVar());
                calledChunk.setRetVarType(iu.getReturnType());
            } else {
                calledChunk.setRetVariable(null);
            }
        }
        calledChunk.getRetIu().setReturnChunk(calledChunk.getChunk());
    }


    private int checkForCaller(int pos) {
        List<Integer> preds = icdg.predecessorListOf(pos);
        for (Integer pred: preds) {
            Edge e = icdg.getEdge(pred, pos);
            try {
                if (e.getEdgeType().equals(EdgeType.CALL_EDGE)) {
                    pos = pred;
                    break;
                }
            } catch (NullPointerException ex) {
                pos = pred;
                break;
            }
        }
        return pos;
    }


    public int getFirstStmt (int pos) {
        int newPos = pos;
        while(pos>=0) {
            newPos = previousFlowEdge(pos);
            if (newPos != pos) {
                pos = newPos;
            } else {
                break;
            }
        }
        return pos;
    }

    public int getLastStmt (int pos) {
        int newPos;
        while(pos < icdg.getLastLine()) {
            newPos = nextFlowEdge(pos);
            if (newPos != pos) {
                pos = newPos;
            } else {
                break;
            }
        }
        return pos;
    }

    public int getCaller(int pos) {
        int startPos = pos;
        StatementInstance iu = icdg.mapNoUnits(pos);
        String currentMethod = iu.getMethod().getSignature();
        ArrayList<Integer> traversed = new ArrayList<>();
        while(pos>=0) {
            Integer cachedPos = analysisCache.getFromCallerCache(pos);
            if (cachedPos != null) {
                pos = cachedPos;
                break;
            }
            traversed.add(pos);
            iu = icdg.mapNoUnits(pos);
            if (iu!=null && !iu.getMethod().getSignature().equals(currentMethod)) {
                break;
            }
            int newPos = previousFlowEdge(pos);
            if (newPos != pos) {
                pos = newPos;
            } else {
                pos = checkForCaller(pos);
                break;
            }
        }
        int caller = pos;
        for(int curPos : traversed){
            analysisCache.putInCallerCache(curPos, caller);
        }
        return analysisCache.getFromCallerCache(startPos);
    }

    public int getCallerRecurse(int pos) {
        StatementInstance iu = icdg.mapNoUnits(pos);
        String currentMethod = iu.getMethod().getSignature();
        return getCallerHelper( previousFlowEdge(pos), currentMethod );
    }

    public int getCallerHelper(int pos, String currentMethod) {
        Integer cachedPos = analysisCache.getFromCallerCache(pos);
        if (cachedPos != null) {
            return cachedPos;
        }
        StatementInstance iu = icdg.mapNoUnits(pos);
        if (iu!=null && !iu.getMethod().getSignature().equals(currentMethod)) {
            analysisCache.putInCallerCache(pos, iu.getLineNo());
            return iu.getLineNo();
        }

        int newPos = previousFlowEdge(pos);
        int caller;
        if (newPos != pos) {
            caller = getCallerHelper(newPos, currentMethod);
        } else {
            caller = checkForCaller(pos);
        }
        analysisCache.putInCallerCache(pos, caller);
        return caller;
    }


    public Pair<AliasSet, AliasSet> changeScopeToCalled(StatementInstance caller, AliasSet aliasSet) {
        AliasSet aliasedArgs = new AliasSet();
        AliasSet remainingSet = new AliasSet(aliasSet);
        AliasSet removedSet = new AliasSet();
        InvokeExpr callerExp = null;
        if (caller.getUnit() instanceof InvokeStmt) {
            callerExp = ((InvokeStmt) caller.getUnit()).getInvokeExpr();
        } else if (caller.getUnit() instanceof AssignStmt) {
            callerExp = ((AssignStmt) caller.getUnit()).getInvokeExpr();
        } else {
            // AnalysisLogger.warn(true, "unsupported call stmt {}", caller);
            return new Pair<>(aliasedArgs, remainingSet);
        }
        
        List<Value> args = callerExp.getArgs();
        addReferenceVariableToArgs(callerExp, args);
        int argPos = 0;
        int inc = 0;
        if (icdg.getSetterCallbackMap().containsKey(new Pair<>(caller.getMethod(), caller.getUnit()))) {
            inc = 1;
        }

        StatementInstance source = getCalledChunk(caller.getLineNo()).getRetIu();
        Map<Integer, AccessPath> argParamMap = getArgParamMapCalled(source, caller, aliasSet, args);

        // for(Value arg: args) {
        //     for (AccessPath ap: aliasSet) {
        //         if (ap.baseEquals(arg.toString())) {
        //             AccessPath aliasedArg = new AccessPath("$r"+String.valueOf(argPos+inc), arg.getType(), ap.getUsedLine(), ap.getDefinedLine(), caller).add(ap.getFields(), ap.getFieldsTypes(), caller);
        //             aliasedArgs.add(aliasedArg);
        //             removedSet.add(ap);
        //         }
        //     }
        //     argPos += 1;
        // }

        for (argPos = 0; argPos < args.size(); argPos++) {
            AccessPath param = argParamMap.get(argPos);
            if (param != null) {
                aliasedArgs.add(param);
            }
        }
        
        for (AccessPath ap: aliasSet) {
            if (ap.isStaticField()) {
                aliasedArgs.add(ap);
                removedSet.add(ap);
            }
        }
        remainingSet = aliasSet.subtract(removedSet);
        return new Pair<>(aliasedArgs, remainingSet);
    }


    public CallerContext getCallerForwardChunk(StatementInstance iu, Set<AccessPath> aliasSet) {
        AliasSet newAliasSet = new AliasSet();
        CallerContext callerContext = analysisCache.getFromCallerForwardChunk(iu);
        if (callerContext == null) {
            callerContext = new CallerContext();
            int firstStmt = getFirstStmt(iu.getLineNo());
            StatementMap firstStmtChunk = getForwardChunk(firstStmt);
            callerContext.setForwardChunk(true);
            callerContext.setCallerChunk(firstStmtChunk);
            callerContext.setCaller(callerContext.getCallerChunk().values().iterator().next());
            analysisCache.putInCallerForwardChunk(iu, callerContext);
        }
        for (Unit uu: callerContext.getCaller().getMethod().getActiveBody().getUnits()) {
            if (uu instanceof IdentityStmt) {
                if (uu.toString().contains("@this") || uu.toString().contains("@parameter")) {
                    String base = uu.getDefBoxes().get(0).getValue().toString();
                    addAliasedAccessPaths(aliasSet, newAliasSet, base);
                }
            } else {
                break;
            }
        }
        addStaticAccessPaths(aliasSet, newAliasSet);
        callerContext.setAliasedArgs(newAliasSet);
        return callerContext;
    }

    private void addAliasedAccessPaths(Set<AccessPath> aliasSet, AliasSet newAliasSet, String base) {
        for (AccessPath ap: aliasSet) {
            if (ap.startsWith(base)) {
                newAliasSet.add(ap);
            }
        }
    }

    private void addStaticAccessPaths(Set<AccessPath> alaisSet, AliasSet newAliasSet) {
        for (AccessPath ap: alaisSet) {
            if (ap.isStaticField()) {
                newAliasSet.add(ap);
            }
        }
    }
    


    public AliasSet changeScope(AliasSet originalAliasSet, StatementInstance source, StatementInstance destination) {
        AliasSet translatedSet = originalAliasSet;
        if (!source.methodEquals(destination)) {
            if (source.getLineNo() > destination.getLineNo()) {
                translatedSet = changeScopeToCaller(source, destination, originalAliasSet);
            } else {
                translatedSet = changeScopeToCalled(source, originalAliasSet).getO1();
            }
        }
        return translatedSet;
    }

    public Map<Integer, AccessPath> getArgParamMapCaller(StatementInstance source, StatementInstance caller, AliasSet aliasSet) {
        Map<Integer, AccessPath> argParamMap = new LinkedHashMap<>();
        Integer argIndex = 0;
        for (Unit uu: source.getMethod().getActiveBody().getUnits()) {
            if (uu instanceof IdentityStmt) {
                if (uu.toString().contains("@this") || uu.toString().contains("@parameter")) {
                    addToParamMapCaller(aliasSet, argParamMap, argIndex, uu);
                    argIndex++;
                }
            } else {
                break;
            }
        }
        return argParamMap;
    }

    public Map<Integer, AccessPath> getArgParamMapCalled(StatementInstance source, StatementInstance caller, AliasSet aliasSet, List<Value> args) {
        Map<Integer, AccessPath> argParamMap = new LinkedHashMap<>();
        Integer argIndex = 0;
        for (Unit uu: source.getMethod().getActiveBody().getUnits()) {
            if (uu instanceof IdentityStmt) {
                if (uu.toString().contains("@this") || uu.toString().contains("@parameter")) {
                    if (argIndex < args.size()) {
                        addToParamMapCalled(aliasSet, argParamMap, argIndex, uu, args.get(argIndex));
                        argIndex++;
                    }
                }
            } else {
                break;
            }
        }

        return argParamMap;
    }

    private void addToParamMapCaller(AliasSet aliasSet, Map<Integer, AccessPath> argParamMap, Integer argIndex, Unit uu) {
        String base = uu.getDefBoxes().get(0).getValue().toString();
        for (AccessPath ap: aliasSet) {
            if (ap.startsWith(base)) {
                argParamMap.put(argIndex, ap);
            }
        }
    }

    private void addToParamMapCalled(AliasSet aliasSet, Map<Integer, AccessPath> argParamMap, Integer argIndex, Unit uu, Value arg) {
        for (AccessPath ap: aliasSet) {
            if (ap.startsWith(arg.toString())) {
                Value argVal = uu.getDefBoxes().get(0).getValue();
                AccessPath argAp = new AccessPath(argVal.toString(), argVal.getType(), ap.getUsedLine(), ap.getDefinedLine(), ap.getStatementInstance());
                AccessPath newAp = new AccessPath(argAp, ap.getStatementInstance()).add(ap.getAfter(argAp).getO1(), ap.getAfter(argAp).getO2(), ap.getStatementInstance()); 
                argParamMap.put(argIndex, newAp);
            }
        }
    }

    public synchronized AliasSet changeScopeToCaller(StatementInstance source, StatementInstance caller, AliasSet aliasSet) {
        AliasSet aliasedArgs = new AliasSet();
        if (caller == null) {
            return aliasedArgs;
        }
        InvokeExpr callerExp = AnalysisUtils.getCallerExp(caller);
        if (callerExp == null) {
            return aliasedArgs;
        }
        
        List<Value> args = callerExp.getArgs();
        addReferenceVariableToArgs(callerExp, args);
        // int inc = 0;
        // if (icdg.getSetterCallbackMap().containsKey(new Pair<>(caller.getMethod(), caller.getUnit()))) {
        //     inc = 1;
        // }
        Map<Integer, AccessPath> argParamMap = getArgParamMapCaller(source, caller, aliasSet);
        int argPos = 0;
        // for(Value arg: args) {
        //     for (AccessPath ap: aliasSet) {
        //         if (ap.getPath().isEmpty()) {
        //             continue;
        //         }
        //         if (!callerExp.getMethod().isStatic() && !ap.getBase().getO1().startsWith("r")) {
        //             inc = 1;
        //         }
        //         String start = ap.getBase().getO1().substring(0, 1);
        //         AnalysisLogger.log(true, "Start: {}", start);
        //         AccessPath p = new AccessPath(start+String.valueOf(argPos), arg.getType(), ap.getUsedLine(), ap.getDefinedLine(), caller);
        //         AnalysisLogger.log(true, "P: {}", p);
        //         p.add(ap.getFields(), ap.getFieldsTypes(), caller);
        //         AnalysisLogger.log(true, "P: {}", p);
        //         translateVaribleToCaller(caller, aliasedArgs, args, inc, argPos, ap, p);
        //         AnalysisLogger.log(true, "AliasedArgs first loop: {}", aliasedArgs);
        //     }
        //     argPos++;
        // }

        for (argPos = 0; argPos < args.size(); argPos++) {
            AccessPath param = argParamMap.get(argPos);
            if (param != null) {
                AccessPath p = new AccessPath(args.get(argPos).toString(), args.get(argPos).getType(), param.getUsedLine(), param.getDefinedLine(), caller);
                p.add(param.getFields(), param.getFieldsTypes(), caller);
                aliasedArgs.add(p);
            }
        }
        // AliasSet removed = new AliasSet();
        // for (AccessPath aliasedArg: aliasedArgs) {
        //     boolean foundMatch = false;
        //     for(Value arg: args) {
        //         AnalysisLogger.log(true, "Comparing: {} to {}", aliasedArg, arg);
        //         if (aliasedArg.startsWith(arg.toString())) {
        //             foundMatch = true;
        //             break;
        //         }
        //     }
        //     if (!foundMatch) {
        //         AnalysisLogger.log(true, "Will remove: {}", aliasedArg);
        //         removed.add(aliasedArg);
        //     }
        // }
        // aliasedArgs = aliasedArgs.subtract(removed);
        propagateStaticVariablesToCaller(aliasSet, aliasedArgs);
        return aliasedArgs;
    }


    private void propagateStaticVariablesToCaller(AliasSet aliasSet, AliasSet aliasedArgs) {
        for (AccessPath v: aliasSet) {
            if (v.isStaticField()) {
                aliasedArgs.add(v);
            }
        }
    }

    private void addReferenceVariableToArgs(InvokeExpr callerExp, List<Value> args) {
        try {
            InstanceInvokeExpr instanceCallerExp = (InstanceInvokeExpr) callerExp;
            args.add(0, instanceCallerExp.getBase());
        } catch (Exception e) {
            // Ignored
        }
    }



    private void translateVaribleToCaller(StatementInstance caller, AliasSet aliasedArgs, List<Value> args, int inc, int argPos,
            AccessPath ap, AccessPath p) {
        try {
            if(p.pathEquals(ap)) {
                Value tArg = args.get(argPos+inc);
                if (!AnalysisUtils.isInteger(tArg.toString())) {
                    AccessPath aliasedArg = new AccessPath(tArg.toString(), tArg.getType(), ap.getUsedLine(), ap.getDefinedLine(), caller).add(p.getFields(), p.getFieldsTypes(), caller);
                    aliasedArgs.add(aliasedArg);
                }
            }
        } catch (IndexOutOfBoundsException e) {
            // Ignored
        }
    }

    public boolean isFrameworkMethod(StatementInstance iu){
        return iu.containsInvokeExpr() && (getCalledChunk(iu.getLineNo()).getChunk() == null);
    }

}
