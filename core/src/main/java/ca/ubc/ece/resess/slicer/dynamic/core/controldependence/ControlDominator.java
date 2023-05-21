package ca.ubc.ece.resess.slicer.dynamic.core.controldependence;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import ca.ubc.ece.resess.slicer.dynamic.core.graph.DynamicControlFlowGraph;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.LazyStatementMap;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementInstance;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementMap;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisLogger;
import soot.toolkits.graph.pdg.EnhancedUnitGraph;
import soot.toolkits.graph.pdg.Region;
import soot.toolkits.graph.pdg.RegionAnalysis;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.GotoStmt;
import soot.toolkits.scalar.Pair;


public class ControlDominator{

    static Set<SootMethod> outOfMemMethods = new LinkedHashSet<>();
    static Map<SootMethod, RegionAnalysis> computedRegions = new LinkedHashMap<>();
    static Map<SootMethod, EnhancedUnitGraph> computedGraphs = new LinkedHashMap<>();
    private ControlDominator() {
        throw new IllegalStateException("Utility class");
    }

    static int getIntFromString(String s) {
        Matcher matcher = Pattern.compile("\\d+").matcher(s);
        matcher.find();
        return Integer.valueOf(matcher.group());
    }

    public static StatementInstance getControlDominator(StatementInstance stmt, LazyStatementMap lazyChunk, DynamicControlFlowGraph dcfg){
        if (outOfMemMethods.contains(stmt.getMethod())) {
            return null;
        }
        //ControlDomRunner cdr = new ControlDomRunner(stmt, lazyChunk, dcfg);
//        Thread t = new Thread(cdr);
//        try {
//            t.start();
//            t.join(30*1000);
//            t.interrupt();
//        } catch (InterruptedException e) {
//            // pass
//        }
        return getControlDom(stmt, lazyChunk, dcfg);
    }


    static class ControlDomRunner implements Runnable {
        private final StatementInstance stmt;
        private final LazyStatementMap lazyChunk;
        private final DynamicControlFlowGraph dcfg;
        private StatementInstance candidateIu;

        ControlDomRunner (StatementInstance stmt, LazyStatementMap lazyChunk, DynamicControlFlowGraph dcfg){
            this.stmt = stmt;
            this.lazyChunk = lazyChunk;
            this.dcfg = dcfg;
        }
        @Override
        public void run() {
            this.candidateIu = getControlDom(stmt, lazyChunk, dcfg);
        }

        public StatementInstance getCandidateIu() {
            return candidateIu;
        }
    }


    static HashMap<Region, Boolean> exceptionRegions = new HashMap<>();
    private static StatementInstance getControlDom(StatementInstance stmt, LazyStatementMap lazyChunk, DynamicControlFlowGraph icdg) {
        StatementInstance candidateIu = null;
        try {
            EnhancedUnitGraph cug;
            RegionAnalysis ra;
            if (computedGraphs.containsKey(stmt.getMethod())) {
                cug = computedGraphs.get(stmt.getMethod());
                ra = computedRegions.get(stmt.getMethod());
            } else {
                cug = new EnhancedUnitGraph(stmt.getMethod().getActiveBody());
                ra = new RegionAnalysis(cug, stmt.getMethod(), stmt.getMethod().getDeclaringClass());
                computedGraphs.put(stmt.getMethod(), cug);
                computedRegions.put(stmt.getMethod(), ra);
            }
            
            // AnalysisLogger.warn(true, "Graph: {}", cug);
            // HashMutablePDG pdg = new HashMutablePDG(new ExceptionalUnitGraph(stmt.getMethod().getActiveBody()));
            for(Region r: ra.getRegions()) {
                List<Unit> regionUnits = r.getUnits();
                if (regionUnits.contains(stmt.getUnit())) {
                    if(!exceptionRegions.containsKey(r)) {
                        if (regionUnits.toString().contains(":= @caughtexception") || regionUnits.toString().contains("goto [?= throw")) {
                            exceptionRegions.put(r, true);
                            candidateIu = lineBeforeException(icdg, regionUnits, stmt);
                        } else {
                            exceptionRegions.put(r, false);
                            candidateIu = matchControlDom(stmt, lazyChunk, cug, ra.getRegions(), r);
                        }
                        continue;
                    }
                    if(exceptionRegions.get(r)){
                        candidateIu = lineBeforeException(icdg, regionUnits, stmt);
                    } else {
                        candidateIu = matchControlDom(stmt, lazyChunk, cug, ra.getRegions(), r);
                    }
                }
            }
        } catch (OutOfMemoryError e1) {
            AnalysisLogger.warn(true, "Could not compute control dom due to OutOfMemoryError");
            outOfMemMethods.add(stmt.getMethod());
        } catch (Exception e2) {
            AnalysisLogger.warn(true, "Could not compute control dom due to exception: {}", e2);
        }
        return candidateIu;
    }


    private static StatementInstance matchControlDom(StatementInstance stmt, LazyStatementMap lazyChunk, EnhancedUnitGraph methodGraph, List<Region> allRegions, Region region) {
        StatementInstance candidateIu = null;
        Unit first = region.getFirst();
        List<Unit> preds = methodGraph.getPredsOf(first);
        for (Unit pred : preds) {
            candidateIu = compareUnits(stmt, lazyChunk, pred);
            if(candidateIu != null){
                return candidateIu;
            }
        }
        return null;
    }

    static HashSet<Pair<StatementInstance, Unit>> processedDoms = new HashSet<>();
    private static StatementInstance compareUnits(StatementInstance stmt, LazyStatementMap lazyChunk, Unit unit) {
        for (StatementInstance iu: lazyChunk) {
            if(iu == null){
                return null;
            }
            Pair<StatementInstance, Unit> curPair = new Pair<>(iu, unit);
            if (iu.getLineNo() > stmt.getLineNo()) {
                continue;
            }
            if(processedDoms.contains(curPair)){
                return null;
            }
            processedDoms.add(curPair);
            if (iu.getUnit().equals(unit)) {
                return iu;
            }
        }
        return null;
    }


    private static StatementInstance lineBeforeException(DynamicControlFlowGraph icdg, List<Unit> regionUnits, StatementInstance statementInstance) {
        StatementInstance prev = null;
        Set<String> mustFind = new HashSet<>();
        for (Unit u: regionUnits) {
            if (u.equals(statementInstance.getUnit())) {
                break;
            }
            if (!u.toString().contains(":= @caughtexception")) {
                String unitString = u.toString() + "::" + u.getJavaSourceStartLineNumber();
                //String unitString = u.toString();
                mustFind.add(unitString);
            }
        }
        int newPos = statementInstance.getLineNo();
        while (newPos > 0) {
            newPos--;
            prev = icdg.mapNoUnits(newPos);
            if ((prev!=null) && !((prev.getUnit()) instanceof GotoStmt)) {
                String unitString = prev.getUnit().toString() + "::" + prev.getJavaSourceLineNo();
                //String unitString = prev.getUnit().toString();
                if (mustFind.isEmpty()) {
                    return prev;
                } else if (mustFind.contains(unitString)) {
                    mustFind.remove(unitString);
                }
            }
            
        }
        return prev;
    }
}