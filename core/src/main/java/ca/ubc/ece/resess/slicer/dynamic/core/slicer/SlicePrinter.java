package ca.ubc.ece.resess.slicer.dynamic.core.slicer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import ca.ubc.ece.resess.slicer.dynamic.core.accesspath.AccessPath;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementInstance;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisLogger;
import guru.nidi.graphviz.attribute.ForNodeLink;
import guru.nidi.graphviz.attribute.Label;
import guru.nidi.graphviz.attribute.Style;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.engine.GraphvizException;
import guru.nidi.graphviz.engine.Rasterizer;
import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.model.Node;
import org.apache.commons.io.FileUtils;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import soot.Unit;
import soot.toolkits.scalar.Pair;

import static guru.nidi.graphviz.model.Factory.*;

public class SlicePrinter {

    private SlicePrinter() {
        throw new IllegalStateException("Utility class");
    }

    public static void printSliceLines(String outDir, DynamicSlice dynamicSlice) {
        String fileName = outDir + File.separator + "slice.log";
        Set<String> printList = new LinkedHashSet<>();
        for (Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>> entry : dynamicSlice) {
            StatementInstance sliceNode = entry.getO1().getO1();
            printList.add(sliceNode.getJavaSourceFile() + ":" + sliceNode.getJavaSourceLineNo());
        }
        try {
            FileUtils.writeLines(new File(fileName), printList);
        } catch (IOException e) {
            AnalysisLogger.warn(true, "Exception when writing slice: {}", e);
        }
    }

    public static void printDotGraph(String outDir, DynamicSlice dynamicSlice) {
        MutableGraph g = mutGraph("Dynamic Slice").setDirected(true);
        // Map<SootMethod, List<Node>> clusters = new LinkedHashMap<>();
        for (Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>> entry : dynamicSlice) {
            String edge = dynamicSlice.getEdges(entry.getO1().getO1().getLineNo(), entry.getO2().getO1().getLineNo());
            StatementInstance sliceNode = entry.getO1().getO1();
            AccessPath sliceEdge = entry.getO2().getO2();
            StatementInstance sourceNode = entry.getO2().getO1();
            Style<ForNodeLink> edgeStyle = Style.SOLID;
            if (edge.equals("control")) {
                edgeStyle = Style.DASHED;
            }
            String sourceStr = sourceNode.getUnit().toString().replace("\\", "");
            if (sourceStr.contains("goto")) {
                sourceStr = sourceStr.split("goto")[0];
            }
            String destStr = sliceNode.getUnit().toString().replace("\\", "");
            if (destStr.contains("goto")) {
                destStr = destStr.split("goto")[0];
            }
            String edgeStr = "";
            if (edge.equals("data")) {
                edgeStr = "    " + sliceEdge.getPathString();
            }

            Node newNode = node(sourceNode.getJavaSourceFile() + ":" + sourceNode.getJavaSourceLineNo() + ": " + sourceStr);
            if (sourceNode.equals(sliceNode)) {
                g.add(newNode);
            } else {
                g.add(newNode.link(
                        to(node(sliceNode.getJavaSourceFile() + ":" + sliceNode.getJavaSourceLineNo() + ": " + destStr)).with(edgeStyle, Label.of(edgeStr))));
            }


            // List<Node> clusterNodes = new ArrayList<>();
            // if (clusters.containsKey(sourceNode.getMethod())) {
            //     clusterNodes = clusters.get(sourceNode.getMethod());
            // }
            // clusterNodes.add(newNode);
            // clusters.put(sourceNode.getMethod(), clusterNodes);
            // clusterNodes = new ArrayList<>();
            // if (clusters.containsKey(sliceNode.getMethod())) {
            //     clusterNodes = clusters.get(sliceNode.getMethod());
            // }
            // clusterNodes.add(newNode);
            // clusters.put(sliceNode.getMethod(), clusterNodes);
        }
        // for (Map.Entry<SootMethod, List<Node>> cluster: clusters.entrySet()) {
        //     MutableGraph subG = mutGraph(cluster.getKey().getSignature()).setCluster(true);
        //     for (Node n: cluster.getValue()) {
        //         subG.add(n);
        //     }
        //     g.add(subG);
        // }
        try {
            // Graphviz.fromGraph(g).render(Format.SVG).toFile(new File(outDir + File.separator + "slice-graph.svg"));
            Graphviz.fromGraph(g).rasterize(Rasterizer.builtIn("pdf")).toFile(new File(outDir + File.separator + "slice-graph.pdf"));
        } catch (IOException | GraphvizException e) {
            AnalysisLogger.warn(true, "Exception when writing slice graph file: {}", e.getMessage());
        }
    }

    public static void printSliceWithDependencies(String outDir, DynamicSlice dynamicSlice) {
        List<String> toPrint = new ArrayList<>();
        toPrint.add("Slice:");
        toPrint.add("---------------------");
        for (Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>> entry : dynamicSlice) {
            String edge = dynamicSlice.getEdges(entry.getO1().getO1().getLineNo(), entry.getO2().getO1().getLineNo());
            StatementInstance sliceNode = entry.getO1().getO1();
            AccessPath sliceEdge = entry.getO2().getO2();
            StatementInstance sourceNode = entry.getO2().getO1();

            String sourceStr = sourceNode.getJavaSourceFile() + ":" + sourceNode.getJavaSourceLineNo();

            String destStr = sliceNode.getUnit().toString().replace("\\", "");
            if (destStr.contains("goto")) {
                destStr = destStr.split("goto")[0];
            }
            destStr = "(" + sliceNode.getJavaSourceFile() + ":" + sliceNode.getJavaSourceLineNo() + ")  " + destStr;
            String edgeStr = "";
            if (edge.equals("data")) {
                edgeStr = ":" + sliceEdge.getPathString();
            }

            String newLine = destStr +
                    " <--" +
                    edge +
                    edgeStr +
                    "-- " +
                    sourceStr;
            if (!toPrint.contains(newLine)) {
                toPrint.add(newLine);
            }
        }
        String fileName = outDir + File.separator + "slice-dependencies.log";
        try {
            FileUtils.writeLines(new File(fileName), toPrint);
        } catch (IOException e) {
            AnalysisLogger.warn(true, "Exception when writing slice: {}", e);
        }
    }


    public static void printRawSlice(String outDir, DynamicSlice dynamicSlice) {
        List<String> staticPrint = new ArrayList<>();
        Set<String> staticSlice = new LinkedHashSet<>();

        for (Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>> entry : dynamicSlice) {
            Pair<StatementInstance, AccessPath> iup = entry.getO1();
            StatementInstance si = iup.getO1();
            String toPrint = si.getJavaSourceFile() + ":" + si.getJavaSourceLineNo() + "    " + si.getThreadID() + "    " + si.getLineNo() + ":" + si.getUnitString();
            if (!staticSlice.contains(toPrint)) {
                staticSlice.add(toPrint);
                staticPrint.add(toPrint);
            }
        }
        String fileName = outDir + File.separator + "raw-slice.log";
        try {
            FileUtils.writeLines(new File(fileName), staticPrint);
        } catch (IOException e) {
            AnalysisLogger.warn(true, "Exception when writing slice: {}", e);
        }
    }


    public static void printSlices(DynamicSlice dynamicSlice) {
        List<String> dynamicPrint = new ArrayList<>();
        List<String> staticPrint = new ArrayList<>();
        Set<String> staticSlice = new LinkedHashSet<>();

        for (Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>> entry : dynamicSlice) {
            Pair<StatementInstance, AccessPath> iup = entry.getO1();
            StatementInstance iu = iup.getO1();
            dynamicPrint.add(iup.toString());
            dynamicPrint.add("   from:" + entry.getO2());
            String toPrint = iu.getMethod().getSignature() + ":" + iu.getUnit().getJavaSourceStartLineNumber() + "-" + iu.getUnit().getJavaSourceStartColumnNumber() + ":" + iu.getUnit().toString();
            if (!staticSlice.contains(toPrint)) {
                staticSlice.add(toPrint);
                staticPrint.add(toPrint);
                staticPrint.add("   from:" + entry.getO2());
            }
        }
        AnalysisLogger.log(true, "Printing dynamic slice:");
        for (String s : dynamicPrint) {
            AnalysisLogger.log(true, "{}", s);
        }
        AnalysisLogger.log(true, "Printing static slice:");
        for (String s : staticPrint) {
            AnalysisLogger.log(true, "{}", s);
        }
    }

    public static void printSliceGraph(DynamicSlice dynamicSlice) {
        SimpleDirectedWeightedGraph<Integer, DefaultWeightedEdge> graph = dynamicSlice.getChopGraph();
        AnalysisLogger.log(true, "Graph:");
        for (Integer v : graph.vertexSet()) {
            List<Integer> nodes = Graphs.predecessorListOf(graph, v);
            for (Integer node : nodes) {
                AnalysisLogger.log(true, "{} --> {}", v, node);
            }
        }
        AnalysisLogger.log(true, "Graph End");
    }


    public static void printToCSV(String fileName, DynamicSlice dynamicSlice) {
        ArrayList<String> printList = new ArrayList<>();
        Set<String> lines = new LinkedHashSet<>();
        printList.add("ID, Method, Class, Line, Source, Var");
        for (Pair<Pair<StatementInstance, AccessPath>, Pair<StatementInstance, AccessPath>> elem : dynamicSlice) {
            StatementInstance iu = elem.getO1().getO1();
            int id = dynamicSlice.getOrder(elem.getO1());
            String method = iu.getMethod().getSubSignature().replace(',', ';');
            String clString = iu.getMethod().getDeclaringClass().getName().replace(',', ';');
            String sourceLineNo = getSourceLineNumber(iu);
            String line = iu.getUnit().toString().replace(',', ';');
            line = method + ", " + clString + ", " + line;
            if (!lines.contains(line)) {
                lines.add(line);
                Pair<StatementInstance, AccessPath> source = elem.getO2();
                int sourceId = dynamicSlice.getOrder(source);
                String toPrint = id + ", " + line + ", " + sourceLineNo + ", " + sourceId + ", " + source.getO2().toString();
                printList.add(toPrint);
            }
        }
        try {
            AnalysisLogger.log(true, "Number of lines: {}", printList.size() - 1);
            FileUtils.writeLines(new File(fileName), printList);
        } catch (IOException e) {
            AnalysisLogger.warn(true, "Exception when writing csv: {}", e);
        }

    }

    private static String getSourceLineNumber(StatementInstance iu) {
        int lineNo = iu.getUnit().getJavaSourceStartLineNumber();
        if (lineNo == -1) {
            int counter = 0;
            for (Unit u : iu.getMethod().getActiveBody().getUnits()) {
                counter++;
                if (u.equals(iu.getUnit())) {
                    lineNo = counter;
                }
            }
        }
        return String.valueOf(lineNo);
    }
}
