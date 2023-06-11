package ca.ubc.ece.resess.slicer.dynamic.core.slicer;


import java.util.*;

import ca.ubc.ece.resess.slicer.dynamic.core.graph.EdgeType;
import org.apache.commons.lang3.tuple.Triple;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;

import ca.ubc.ece.resess.slicer.dynamic.core.accesspath.AccessPath;
import ca.ubc.ece.resess.slicer.dynamic.core.graph.DynamicControlFlowGraph;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementInstance;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisLogger;
import soot.jimple.ReturnVoidStmt;
import soot.toolkits.scalar.Pair;

public class DynamicSlice
        extends ArrayList<Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>>> {

    private static final long serialVersionUID = 1L;

    private SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> chopGraph = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
    private Map<Integer, Integer> methodOfStatement = new LinkedHashMap<>();
    enum EdgeType {
        data,
        control
    };
    private Map<EdgeType, Set<Pair<Integer, Integer>>> edgeTypes = new HashMap<>();
    class OrderedSlice extends ArrayList<Pair<StatementInstance, AccessPath>> {

        private static final long serialVersionUID = 7286928364073066546L;

    }

    OrderedSlice order = new OrderedSlice();


    public DynamicSlice(){
        super();
        for(EdgeType e : EdgeType.values()){
            edgeTypes.put(e, new HashSet<>());
        }
        // String s = "";
        // for (StackTraceElement ste: Thread.currentThread().getStackTrace())  {
        //     s += ste.toString();
        // }
        // AnalysisLogger.log(true, "Called at {}", s);
    }

    public DynamicSlice(Collection<Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>>> collection){
        super( collection );
        for(EdgeType e : EdgeType.values()){
            edgeTypes.put(e, new HashSet<>());
        }
    }

    public boolean hasEdge(Integer src, Integer dst, String edgeType) {
        // AnalysisLogger.log(true, "Checking edge: {}-{}-{}", src, dst, edgeType);
        // AnalysisLogger.log(true, "Found it!");
        return edgeTypes.get(EdgeType.valueOf(edgeType)).contains(new Pair<>(src, dst));
    }

    public String getEdges(Integer src, Integer dst) {
        StringBuilder edges = new StringBuilder();
        for(EdgeType e : EdgeType.values()){
            if(edgeTypes.get(e).contains(new Pair<>(src, dst))){
                edges.append(e.toString());
            }
        }
        return edges.toString();
    }

    public boolean add(Pair<StatementInstance, AccessPath> key,
            Pair<StatementInstance, AccessPath> value, String edgeType) {
        edgeTypes.get(EdgeType.valueOf(edgeType)).add(new Pair<>(key.getO1().getLineNo(), value.getO1().getLineNo()));
        // AnalysisLogger.log(true, "Added edge: {}-{}-{}", key.getO1().getLineNo(), value.getO1().getLineNo(), edgeType);
        return this.add(key, value);
    }

    @Override
    public boolean add(Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>> val) {
        throw new IllegalStateException("Should not call this method");
    }
    public boolean add(Pair<StatementInstance, AccessPath> key,
            Pair<StatementInstance, AccessPath> value) {
        

        // AnalysisLogger.log(true, "Graph: {} of Slice {}", System.identityHashCode(chopGraph), System.identityHashCode(this));
        // for (Integer v: chopGraph.vertexSet()) {
        //     List<Integer> nodes = Graphs.successorListOf(chopGraph, v);
        //     for (Integer node: nodes) {
        //         AnalysisLogger.log(true, "{} --> {}", v, node);
        //     }
        // }
        

        chopGraph.addVertex(key.getO1().getLineNo());
        if (!chopGraph.containsVertex(value.getO1().getLineNo())) {
            chopGraph.addVertex(value.getO1().getLineNo());
        }
        
        
        if (key.getO1().getLineNo() != value.getO1().getLineNo()) {
            // AnalysisLogger.log(true, "Added edge {} --> {}", key.getO1().getLineNo(), value.getO1().getLineNo());
            chopGraph.addEdge(key.getO1().getLineNo(), value.getO1().getLineNo());
        }
        
        if (!(key.getO1().getUnit() instanceof ReturnVoidStmt)) {
            order.add(key);
            return super.add(new Pair<>(key, value));
        }
        
        return false;
    }



    public void addMethod(Integer pos, Integer method){
        methodOfStatement.put(pos, method);
        // AnalysisLogger.log(true, "Added Method {} --> {}", pos, method);
    }

    public Integer getOrder(Pair<StatementInstance, AccessPath> key) {
        return order.indexOf(key);
    }

    public DynamicSlice reorder() {
        DynamicSlice ordered = new DynamicSlice();
        ordered.chopGraph = chopGraph;
        ordered.methodOfStatement = methodOfStatement;
        ordered.edgeTypes = edgeTypes;
        List<Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>>> orderedList = new ArrayList<>();
        orderedList.addAll(this);
        Collections.sort(orderedList, (lhs, rhs) -> {
            if (rhs.getO1().getO1().getLineNo() > lhs.getO1().getO1().getLineNo()) {
                return 1;
            } else if (rhs.getO1().getO1().getLineNo() <= lhs.getO1().getO1().getLineNo()) {
                return -1;
            }
            return 0;
        });
        for (Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>> iup: orderedList) {
            // if (!ordered.containsKey(iup)) {
                ordered.add(iup.getO1(), iup.getO2());
            // }
        }
        return ordered;
    }


    public DynamicSlice traceOrder() {
        DynamicSlice ordered = new DynamicSlice();
        ordered.chopGraph = chopGraph;
        ordered.methodOfStatement = methodOfStatement;
        ordered.edgeTypes = edgeTypes;
        List<Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>>> orderedList = new ArrayList<>();
        orderedList.addAll(this);
        Collections.sort(orderedList, (lhs, rhs) -> {
            if (rhs.getO1().getO1().getLineNo() < lhs.getO1().getO1().getLineNo()) {
                return 1;
            } else if (rhs.getO1().getO1().getLineNo() > lhs.getO1().getO1().getLineNo()) {
                return -1;
            }
            return 0;
        });
        for (Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>> iup: orderedList) {
            // if (!ordered.containsKey(iup)) {
                ordered.add(iup.getO1(), iup.getO2());
            // }
        }
        return ordered;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    public SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> getChopGraph() {
        return chopGraph;
    }

    public SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> chop(int forwSlicePos, DynamicControlFlowGraph icdg) {

        // AnalysisLogger.log(true, "Graph:");
        // for (Integer v: chopGraph.vertexSet()) {
        //     List<Integer> nodes = Graphs.successorListOf(chopGraph, v);
        //     for (Integer node: nodes) {
        //         AnalysisLogger.log(true, "{} --> {}", v, node);
        //     }
        // }

        SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> chop = new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        AnalysisLogger.log(true, "Chopping from {}", forwSlicePos);
        if (!chopGraph.containsVertex(forwSlicePos)) {
            AnalysisLogger.log(true, "No vertix :(");
            return chop;
        }
        
        for (Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>> n: this) {
            if (n.getO1().getO1().getLineNo() == forwSlicePos) {
                addToChop(n, chop, icdg);
            }
        }
        return chop;
    }

    private void addToChop(Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>>start, SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> chop, DynamicControlFlowGraph icdg) {
        // AnalysisLogger.log(true, "Adding to chop: {}", start);
        Pair<StatementInstance, AccessPath> next = start.getO2();
        int startPos = start.getO1().getO1().getLineNo();
        int nextPos = next.getO1().getLineNo();
        if ((!chop.containsEdge(startPos, nextPos)) && (startPos != nextPos)) {
            // AnalysisLogger.log(true, "Adding to chop: {} --> {}", startPos, nextPos);
            chop.addVertex(startPos);
            chop.addVertex(nextPos);
            chop.addEdge(startPos, nextPos);
            addCallerToChop(start, chop);
        } else {
            return;
        }
        List<Integer> nodes = Graphs.successorListOf(chopGraph, nextPos);
        // AnalysisLogger.log(true, "Successors are: {}", nodes);
        for (Integer node: nodes) {
            if (!chop.containsVertex(node)) {
                for (Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>> n: this) {
                    if (n.getO1().getO1().getLineNo() == node) {
                        // AnalysisLogger.log(true, "Recursive call on: {}", n);
                        addToChop(n, chop, icdg);
                    }
                }
            }
        }
    }

    private void addCallerToChop(Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>> start, SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> chop) {
        try {
            int startPos = start.getO1().getO1().getLineNo();
            int callerPos = methodOfStatement.get(startPos);
            for (Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>> n: this) {
                if (n.getO1().getO1().getLineNo() == callerPos) {
                    chop.addVertex(n.getO1().getO1().getLineNo());
                    chop.addVertex(n.getO2().getO1().getLineNo());
                    chop.addEdge(n.getO1().getO1().getLineNo(), n.getO2().getO1().getLineNo());
                    break;
                }
            }
        } catch (NullPointerException e) {
            // pass
        }
    }


    public Map<StatementInstance, String> getSliceDependenciesAsMap() {

        Map<StatementInstance, String> sliceDeps = new HashMap<>();
        for(Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>> entry: this) {
            String edge = getEdges(entry.getO1().getO1().getLineNo(), entry.getO2().getO1().getLineNo());
            StatementInstance sourceNode = entry.getO2().getO1();
            StatementInstance destinationNode = entry.getO1().getO1();
            String sourceString = ", source:" + + sourceNode.getLineNo();
            String edgeStr = "control, no variable";
            AccessPath sliceEdge = entry.getO2().getO2();
            if (edge.equals("data")) {
                edgeStr = "data, varaible:" + sliceEdge.getPathString();
            }
            if (sliceEdge.getPathString().isEmpty()) {
                edgeStr = "start";
                sourceString = "";
            }
            sliceDeps.put(destinationNode, edgeStr + sourceString);
        }
        return sliceDeps;
    }

    public Set<String> getSliceAsSourceLineNumbers() {
        Set<String> sliceLines = new HashSet<>();
        for(Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>> entry: this) {
            StatementInstance destinationNode = entry.getO1().getO1();
            if (destinationNode.getJavaSourceLineNo() != -1) {
                sliceLines.add(destinationNode.getJavaSourceFile() + ":" + destinationNode.getJavaSourceLineNo());
            }
        }
        return sliceLines;
    }

    public void union(DynamicSlice other) {
        this.addAll(other);
        for (Integer v: other.chopGraph.vertexSet()) {
            this.chopGraph.addVertex(v);
        }
        for (DefaultWeightedEdge e: other.chopGraph.edgeSet()) {
            Graphs.addEdgeWithVertices(this.chopGraph, other.chopGraph, e);
        }

        this.methodOfStatement.putAll(other.methodOfStatement);
        //this.edgeTypes.addAll(other.edgeTypes);
        return;
    }
}

