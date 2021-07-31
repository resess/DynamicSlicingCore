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

public class SliceForward extends SliceMethod {

    public SliceForward(DynamicControlFlowGraph icdg, boolean frameworkModel, boolean dataFlowsOnly, boolean controlFlowOnly, boolean sliceOnce, SlicingWorkingSet workingSet) {
        super(icdg, frameworkModel, dataFlowsOnly, controlFlowOnly, sliceOnce, workingSet);
    }

    @Override
    public StatementSet localReachingDef(StatementInstance iu, AccessPath ap, StatementMap chunk, AliasSet usedVars, boolean frameworkModel) {
        StatementSet useSet = new StatementSet();
        StatementSet usesInCalled = null;
        if (ap.isEmpty() || chunk == null) {
            return useSet;
        }
        chunk = chunk.inTraceOrder(iu);
        boolean localFound = false;
        for (StatementInstance u: chunk.values()) {
            if (localFound) {
                break;
            }
            AnalysisLogger.log(Constants.DEBUG, "Inspecting {}", u);
            if (u.getLineNo() >= iu.getLineNo() || u.getUnit()==null) {
                continue;
            }
            for (ValueBox use: u.getUnit().getUseBoxes()) {
                AnalysisLogger.log(Constants.DEBUG, "Inspecting use {}", use);
                if(use.getValue() instanceof Local) {
                    if (ap.baseEquals(use.getValue().toString())) {
                        useSet.add(u);
                        localFound = true;
                        break;
                    }
                } else if (use.getValue() instanceof FieldRef){
                    for (ValueBox vb: ((FieldRef) use.getValue()).getUseBoxes()){
                        if (ap.baseEquals(vb.getValue().toString())) {
                            useSet.add(u);
                        }
                    }
                } else if (use.getValue() instanceof ArrayRef) {
                    Value v = ((ArrayRef) use.getValue()).getBase();
                    if (ap.baseEquals(v.toString())) {
                        useSet.add(u);
                    }
                }
            }
            InvokeExpr invokeExpr = AnalysisUtils.getCallerExp(u);
            if (invokeExpr != null) {
                if (!traversal.isFrameworkMethod(u)) {
                    if (! (((Stmt) u.getUnit()) instanceof AssignStmt)) {
                        if (invokeExpr != null && !invokeExpr.getMethod().isStatic()) {
                            if (ap.baseEquals(((InstanceInvokeExpr) invokeExpr).getBase().toString())){
                                useSet.add(u);
                            }
                        }
                    }
                } else if (frameworkModel) {
                    // if (FrameworkModel.localWrapperForward(u, ap, useSet, usedVars)) {
                    //     // pass
                    // }
                }
            }
            if (invokeExpr != null && !traversal.isFrameworkMethod(u) && usesInCalled == null) {
                AliasSet aliasesInCalled = traversal.changeScopeToCalled(u, new AliasSet(ap)).getO1();
                for (AccessPath varInCalled: aliasesInCalled) {
                    StatementMap calledChunk = traversal.getCalledChunk(u.getLineNo()).getChunk();
                    usesInCalled = localReachingDef(iu, varInCalled, calledChunk, usedVars, frameworkModel);
                    AnalysisLogger.log(Constants.DEBUG, "Added defs from called {}", usesInCalled);
                    useSet.addAll(usesInCalled);
                }
            }
        }
        if (useSet.isEmpty()) {
            useSet.addAll(getReachingInCaller(iu, ap));
        }
        AnalysisLogger.log(Constants.DEBUG, "uses forward are: {}", useSet);
        return useSet;
    }
}