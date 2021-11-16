package ca.ubc.ece.resess.slicer.dynamic.core.slicer;

import java.util.LinkedHashSet;
import java.util.Set;

import ca.ubc.ece.resess.slicer.dynamic.core.accesspath.AccessPath;
import ca.ubc.ece.resess.slicer.dynamic.core.accesspath.AliasSet;
import ca.ubc.ece.resess.slicer.dynamic.core.controldependence.ControlDominator;
import ca.ubc.ece.resess.slicer.dynamic.core.framework.FrameworkModel;
import ca.ubc.ece.resess.slicer.dynamic.core.graph.CalledChunk;
import ca.ubc.ece.resess.slicer.dynamic.core.graph.DynamicControlFlowGraph;
import ca.ubc.ece.resess.slicer.dynamic.core.graph.Traversal;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementInstance;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementMap;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementSet;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisCache;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisLogger;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisUtils;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.Constants;
import soot.Local;
import soot.Value;
import soot.ValueBox;
import soot.jimple.ArrayRef;
import soot.jimple.AssignStmt;
import soot.jimple.CastExpr;
import soot.jimple.FieldRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.toolkits.scalar.Pair;

public class SliceMethod {
    protected final DynamicControlFlowGraph icdg;
    protected final Traversal traversal;
    protected final AnalysisCache analysisCache;
    protected final boolean frameworkModel;
    private final boolean dataFlowsOnly;
    private final boolean controlFlowOnly;
    private final boolean sliceOnce;
    private final SlicingWorkingSet workingSet;

    public SliceMethod(DynamicControlFlowGraph icdg, boolean frameworkModel, boolean dataFlowsOnly, boolean controlFlowOnly, boolean sliceOnce, SlicingWorkingSet workingSet, AnalysisCache analysisCache) {
        this.icdg = icdg;
        this.analysisCache = analysisCache;
        this.traversal = new Traversal(icdg, this.analysisCache);
        this.frameworkModel = frameworkModel;
        this.dataFlowsOnly = dataFlowsOnly;
        this.controlFlowOnly = controlFlowOnly;
        this.sliceOnce = sliceOnce;
        if (workingSet != null) {
            this.workingSet = workingSet;
        } else {
            this.workingSet = new SlicingWorkingSet(false);
        }
    }

    public DynamicSlice slice(StatementInstance start, Set<AccessPath> variables) {
        StatementInstance firstDom = null;
        StatementInstance dom;

        if (variables.isEmpty()) {
            workingSet.addStmt(start, new Pair<>(start, new AccessPath(start.getLineNo(), AccessPath.NOT_DEFINED, start)), "data");
            if (start.getCalledMethod() != null) {
                CalledChunk chunk = traversal.getCalledChunk(start.getLineNo());
                if (chunk.getChunk() != null && !chunk.getChunk().isEmpty()) {
                    workingSet.addStmt(chunk.getRetIu(), new Pair<>(start, new AccessPath(chunk.getRetIu().getLineNo(), AccessPath.NOT_DEFINED, chunk.getRetIu())), "data");
                }
            }

        } else {
            workingSet.addMultiple(start, variables, "data");
        }
        while (true) {

            Pair<StatementInstance, AccessPath> p;
            synchronized (workingSet) {
                if (!workingSet.isEmpty()) {
                    p = workingSet.pop();
                } else {
                    break;
                }
            }

            if (sliceOnce) {
                if (!(p.getO1().getJavaSourceLineNo().equals(start.getJavaSourceLineNo())) || !(p.getO1().getJavaSourceFile().equals(start.getJavaSourceFile()))) {
                    continue;
                }
            }

            StatementInstance stmt = p.getO1();
            AccessPath var = p.getO2();
            if (AnalysisUtils.isAndroidMethod(stmt, var)) {
                continue;
            }

            StatementMap chunk = traversal.getChunk(stmt);
            AnalysisLogger.log(Constants.DEBUG, "Slicing on {}", p);
            AnalysisLogger.log(Constants.DEBUG, "With chunk {}", chunk);
            if (!dataFlowsOnly) {
                dom = getControlDependence(workingSet, p, stmt, chunk);
                AnalysisLogger.log(Constants.DEBUG, "Control-dom is {}", dom);
                if ((firstDom != null) && firstDom.equals(dom)) {
                    workingSet.removeAllWithStmt(dom);
                }
            }

            StatementSet def = new StatementSet();
            AliasSet usedVars = new AliasSet();


            if (!controlFlowOnly) {
                def = getDataDependence(workingSet, p, stmt, var, chunk, def, usedVars);
            }

            if (def != null && !def.isEmpty()) {
                if (sliceOnce) {
                    def = new StatementSet(def.iterator().next());
                }
                addDataDependenceToWorkingSet(workingSet, p, var, def);
            }
        }
        return workingSet.getDynamicSlice().traceOrder();
    }

    private void addDataDependenceToWorkingSet(SlicingWorkingSet workingSet, Pair<StatementInstance, AccessPath> p, AccessPath var,
                                               StatementSet def) {
        for (StatementInstance iu : def) {
            if (iu != null) {
                Pair<CalledChunk, AccessPath> retPair = traversal.getReturnIfStmtIsCall(iu.getLineNo());
                if (retPair != null) {
                    iu = retPair.getO1().getRetIu();
                }
                AnalysisLogger.log(Constants.DEBUG && !def.contains(iu), "Return def {}\n", iu);
                if (retPair != null) {
                    workingSet.add(iu, retPair.getO2(), p, "data");
                } else {
                    workingSet.addStmt(iu, p, "data");
                    if (var.getField().equals("")) {
                        getUsedVariables(workingSet, p, iu);
                    }
                }
            }
        }
    }

    private void getUsedVariables(SlicingWorkingSet workingSet, Pair<StatementInstance, AccessPath> p, StatementInstance iu) {
        for (ValueBox fieldDef : iu.getUnit().getDefBoxes()) {
            if (fieldDef.getValue() instanceof FieldRef) {
                for (ValueBox vb : fieldDef.getValue().getUseBoxes()) {
                    if (p.getO2().baseEquals(vb.getValue().toString())) {
                        workingSet.add(iu, p.getO2(), p, "data");
                    }
                }
            }
        }
        if (iu.getCalledMethod() != null && iu.getCalledMethod().getSignature().equals("<java.lang.Object: void <init>()>")) {
            if (((InstanceInvokeExpr) ((Stmt) iu.getUnit()).getInvokeExpr()).getBaseBox().toString().equals(p.getO2().getPathString())) {
                workingSet.add(iu, p.getO2(), p, "data");
            }
        }
    }

    public StatementSet getDataDependence(SlicingWorkingSet workingSet, Pair<StatementInstance, AccessPath> p,
                                          StatementInstance stmt, AccessPath var, StatementMap chunk, StatementSet def, AliasSet usedVars) {
        if (var.getField().equals("")) {
            def = localReachingDef(stmt, var, chunk, usedVars, frameworkModel);
            AnalysisLogger.log(Constants.DEBUG, "Local def {}", def);
        }
        if (!usedVars.isEmpty() && def != null) {
            for (StatementInstance iu : def) {
                for (AccessPath usedVar : usedVars) {
                    workingSet.add(iu, usedVar, p, "data");
                }
            }
        }

        return def;
    }

    private StatementInstance getControlDependence(SlicingWorkingSet workingSet, Pair<StatementInstance, AccessPath> p,
                                                   StatementInstance stmt, StatementMap chunk) {
        StatementInstance dom = ControlDominator.getControlDominator(stmt, chunk, this.icdg);
        if (dom != null) {
            workingSet.addStmt(dom, p, "control");
        } else {
            dom = null;
            try {
                dom = icdg.mapNoUnits(traversal.getCaller(stmt.getLineNo()));
                AnalysisLogger.log(Constants.DEBUG, "Got caller: {}", dom);
            } catch (Exception e) {
                AnalysisLogger.warn(true, "Exception ignored", e);
            }
            if (dom != null && ((Stmt) dom.getUnit()).containsInvokeExpr() && ((Stmt) dom.getUnit()).getInvokeExpr().getMethod().getName().equals(stmt.getMethod().getName())) {
                workingSet.addStmtOnly(dom, p, "control");
                workingSet.addMethodOfStmt(dom, p);
            }
        }
        return dom;
    }

    StatementInstance getCallStmt(StatementMap chunk) {
        StatementInstance c = null;
        for (StatementInstance u : chunk.values()) {
            c = u;
        }
        return c;
    }

    public StatementSet localReachingDef(StatementInstance iu, AccessPath ap, StatementMap chunk, AliasSet usedVars, boolean frameworkModel) {
        AnalysisLogger.log(Constants.DEBUG, "Getting localDef at: {}", iu);
        StatementInstance caller = icdg.mapNoUnits(traversal.getCaller(chunk.values().iterator().next().getLineNo()));
        StatementSet defSet = new StatementSet();
        StatementSet defsInCalled = null;
        if (ap.isEmpty() || chunk == null) {
            return defSet;
        }
        Set<Pair<StatementInstance, AccessPath>> backwardDefVars = new LinkedHashSet<>();
        chunk = chunk.reverseTraceOrder(iu);
        boolean localFound = false;
        StatementInstance prevUnit = null;
        for (StatementInstance u : chunk.values()) {
            AnalysisLogger.log(Constants.DEBUG, "Inspecting: {}", u);
            if (localFound) {
                break;
            }
            if (u.getLineNo() >= iu.getLineNo() || u.getUnit() == null) {
                continue;
            }
            if (u.isReturn()) {
                if (caller.getCalledMethod().getSignature().equals("<java.lang.StringBuilder: java.lang.StringBuilder append(java.lang.Object)>")) {
                    defSet.add(u);
                    defSet.add(caller);
                }
            }
            for (ValueBox def : u.getUnit().getDefBoxes()) {
                if (def.getValue() instanceof Local) {
                    if (ap.baseEquals(def.getValue().toString())) {
                        backwardDefVars.add(new Pair<>(u, new AccessPath(def.getValue().toString(), def.getValue().getType(), AccessPath.NOT_USED, u.getLineNo(), u)));
                        defSet.add(u);
                        localFound = true;
                        break;
                    }
                } else if (def.getValue() instanceof FieldRef) {
                    for (ValueBox vb : def.getValue().getUseBoxes()) {
                        if (ap.baseEquals(vb.getValue().toString())) {
                            backwardDefVars.add(new Pair<>(u, new AccessPath(def.getValue().toString(), def.getValue().getType(), AccessPath.NOT_USED, u.getLineNo(), u)));
                            defSet.add(u);
                        }
                    }
                } else if (def.getValue() instanceof ArrayRef) {
                    Value v = ((ArrayRef) def.getValue()).getBase();
                    if (ap.baseEquals(v.toString())) {
                        backwardDefVars.add(new Pair<>(u, new AccessPath(def.getValue().toString(), def.getValue().getType(), AccessPath.NOT_USED, u.getLineNo(), u)));
                        defSet.add(u);
                    }
                }
            }
            if (u.getUnit() instanceof AssignStmt) {
                Value right = ((AssignStmt) u.getUnit()).getRightOp();
                if (right instanceof FieldRef) {
                    for (ValueBox vb : right.getUseBoxes()) {
                        if (ap.baseEquals(vb.getValue().toString())) {
                            if (prevUnit != null && frameworkModel && traversal.isFrameworkMethod(prevUnit)) {
                                Value left = ((AssignStmt) u.getUnit()).getLeftOp();
                                AccessPath leftAp = new AccessPath(left.toString(), left.getType(), AccessPath.NOT_USED, u.getLineNo(), u);
                                if (FrameworkModel.localWrapper(prevUnit, leftAp, defSet, usedVars)) {
                                    // pass
                                }
                            }
                        }
                    }
                }
            }
            InvokeExpr invokeExpr = AnalysisUtils.getCallerExp(u);
            AnalysisLogger.log(Constants.DEBUG, "Invoke expr {}", invokeExpr);
            if (invokeExpr != null) {
                if (!traversal.isFrameworkMethod(u)) {
                    if (!(u.getUnit() instanceof AssignStmt)) {
                        if (invokeExpr != null && !invokeExpr.getMethod().isStatic()) {
                            if (ap.baseEquals(((InstanceInvokeExpr) invokeExpr).getBase().toString())) {
                                defSet.add(u);
                            }
                        }
                    }
                } else if (frameworkModel) {
                    if (FrameworkModel.localWrapper(u, ap, defSet, usedVars)) {
                        // pass
                    }
                }
            }
            if (invokeExpr != null && !traversal.isFrameworkMethod(u) && defsInCalled == null) {
                AliasSet aliasesInCalled = traversal.changeScopeToCalled(u, new AliasSet(ap)).getO1();
                for (AccessPath varInCalled : aliasesInCalled) {
                    StatementMap calledChunk = traversal.getCalledChunk(u.getLineNo()).getChunk();
                    defsInCalled = localReachingDef(iu, varInCalled, calledChunk, usedVars, frameworkModel);
                    defSet.addAll(defsInCalled);
                }
            }
            prevUnit = u;
        }
        if (defSet.isEmpty()) {
            defSet.addAll(getReachingInCaller(iu, ap));
        }
        // defSet = localReachingDefForward(backwardDefVars, defSet);
        // AnalysisLogger.log(Constants.DEBUG, "Defs with forward are: {}", defSet);
        return defSet;
    }


    private StatementSet localReachingDefForward(Set<Pair<StatementInstance, AccessPath>> backwardDefVars, StatementSet defSet) {

        for (Pair<StatementInstance, AccessPath> p : backwardDefVars) {
            findDefsForward(p, defSet);
        }
        return defSet;
    }

    private void findDefsForward(Pair<StatementInstance, AccessPath> p, StatementSet defSet) {
        StatementInstance si = p.getO1();
        AliasSet as = new AliasSet(p.getO2());
        int pos = si.getLineNo();
        iterateDefsForward(as, defSet, pos);
    }

    private void iterateDefsForward(AliasSet as, StatementSet defSet, int pos) {
        if (as.isEmpty()) {
            return;
        }
        StatementInstance current = icdg.mapNoUnits(pos);
        Set<StatementInstance> nexts = traversal.nextNodes(current);
        for (StatementInstance next : nexts) {
            AliasSet newAs = traversal.changeScope(as, next, current);
            for (AccessPath ap : newAs) {
                for (ValueBox def : next.getUnit().getDefBoxes()) {
                    // AnalysisLogger.log(Constants.DEBUG, "Inspecting def {}", def);
                    if (def.getValue() instanceof FieldRef) {
                        for (ValueBox vb : def.getValue().getUseBoxes()) {
                            if (ap.baseEquals(vb.getValue().toString())) {
                                defSet.add(next);
                            }
                        }
                    } else if (def.getValue() instanceof ArrayRef) {
                        Value v = ((ArrayRef) def.getValue()).getBase();
                        if (ap.baseEquals(v.toString())) {
                            defSet.add(next);
                        }
                    } else if ((next.getUnit() instanceof AssignStmt) && (((AssignStmt) next.getUnit()).getRightOp() instanceof CastExpr)) {
                        Value v = ((AssignStmt) next.getUnit()).getRightOp();
                        if (ap.getPathString().equals(v.toString())) {
                            defSet.add(next);
                        }
                    }
                }
            }
            iterateDefsForward(newAs, defSet, next.getLineNo());
        }
    }

    public StatementSet getReachingInCaller(StatementInstance iu, AccessPath ap) throws Error {
        StatementSet defSet = new StatementSet();
        int callerPos = traversal.getCaller(iu.getLineNo());
        AliasSet apSet = new AliasSet();
        apSet.add(ap);
        AliasSet taintedParams = traversal.changeScopeToCaller(iu, icdg.mapNoUnits(callerPos), apSet);
        if (taintedParams == null || taintedParams.isEmpty()) {
            return defSet;
        }
        if (taintedParams.size() > 1) {
            throw new Error("More than one definition of a local variable!");
        }
        StatementInstance nextCaller = null;
        ap = taintedParams.iterator().next();
        StatementMap callerChunk = traversal.getChunk(callerPos);
        for (StatementInstance u : callerChunk.values()) {
            if (u.getLineNo() == callerPos) {
                nextCaller = u;
            }
            if (u.getUnit() == null || u.getLineNo() == callerPos) {
                continue;
            }
            nextCaller = u;

            boolean foundLocalDef = findLocalDefs(ap, defSet, u);

            if (foundLocalDef) {
                return defSet;
            }

            foundLocalDef = findLocalDefInFrameworkMethod(ap, defSet, u, foundLocalDef);

            if (foundLocalDef) {
                return defSet;
            }
        }

        if (nextCaller != null && nextCaller.equals(iu)) {
            return defSet;
        }
        if (nextCaller != null) {
            return getReachingInCaller(nextCaller, ap);
        } else {
            return defSet;
        }

    }

    public boolean findLocalDefInFrameworkMethod(AccessPath ap, StatementSet defSet, StatementInstance u, boolean foundLocalDef)
            throws Error {
        if (frameworkModel) {
            InvokeExpr invokeExpr = AnalysisUtils.getCallerExp(u);
            if (!(u.getUnit() instanceof AssignStmt)) {
                if (invokeExpr != null && !invokeExpr.getMethod().isStatic()) {
                    if (ap.baseEquals(((InstanceInvokeExpr) invokeExpr).getBase().toString())) {
                        defSet.add(u);
                        foundLocalDef = true;
                    }
                }
            }
        }
        return foundLocalDef;
    }

    public boolean findLocalDefs(AccessPath ap, StatementSet defSet, StatementInstance u) {
        boolean foundLocalDef = false;
        for (ValueBox def : u.getUnit().getDefBoxes()) {
            if (def.getValue() instanceof Local) {
                if (ap.baseEquals(def.getValue().toString())) {
                    defSet.add(u);
                    foundLocalDef = true;
                }
            } else if (def.getValue() instanceof FieldRef) {
                for (ValueBox vb : def.getValue().getUseBoxes()) {
                    if (ap.baseEquals(vb.getValue().toString())) {
                        defSet.add(u);
                    }
                }
            } else if (def.getValue() instanceof ArrayRef) {
                Value v = ((ArrayRef) def.getValue()).getBase();
                if (ap.baseEquals(v.toString())) {
                    defSet.add(u);
                }
            }
        }
        return foundLocalDef;
    }
}