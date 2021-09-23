package ca.ubc.ece.resess.slicer.dynamic.core.graph;

import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementInstance;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.AnalysisLogger;
import ca.ubc.ece.resess.slicer.dynamic.core.utils.Constants;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import soot.*;
import soot.util.Chain;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class TraceTranslator {


    TraceTranslator() {
        throw new IllegalStateException("Utility class");
    }


    public static void translateTraceFile(String staticLogFile, String traceName, String outDir) {
        File outTranslatedTrace = new File(outDir, "translated-trace.log");
        if (outTranslatedTrace.isFile()) {
            outTranslatedTrace.delete();
        }
        Map<Long, List<String>> logMap = new HashMap<>();
        JSONParser parser = new JSONParser();
        try {
            AnalysisLogger.log(true, "Abs path for static-log {}", new File(staticLogFile).getAbsolutePath());
            FileReader reader = new FileReader(staticLogFile);
            Object obj = parser.parse(reader);
            JSONObject jObj = (JSONObject) obj;
            Parser.buildLogMap(jObj, logMap);
        } catch (IOException | ParseException e) {
            AnalysisLogger.warn(true, "Cannot read static-log file! {}", e);
        }
        String lastSlicingLine = "";
        try (BufferedReader br = new BufferedReader(new FileReader(traceName))) {
            String t;
            while ((t = br.readLine()) != null) {
                Trace listTraces = new Trace();
                try {
                    t = t.split("SLICING:")[1];
                    if (lastSlicingLine.equals(t)) {
                        continue;
                    }
                    lastSlicingLine = t;
                } catch (ArrayIndexOutOfBoundsException e) {
                    continue;
                }
                if (t.startsWith(" ZLIB: ")) {
                    t = Parser.decompress(t.split(" ZLIB: ")[1]);
                }
                String[] chunk = t.split("-");
                for (String s : chunk) {
                    int fieldId = -1;
                    long lineNum;
                    long threadNum;
                    String[] sSplit = s.split(":");
                    try {
                        lineNum = Long.parseLong(sSplit[0]);
                    } catch (java.lang.NumberFormatException e) {
                        continue;
                    }
                    threadNum = Long.parseLong(sSplit[1]);
                    try {
                        fieldId = Integer.parseInt(sSplit[2]);
                    } catch (ArrayIndexOutOfBoundsException e) {
                        // Ignored
                    }
                    Parser.addToExpandedTrace(listTraces, logMap, lineNum, threadNum, fieldId);

                }
                List<StatementInstance> statementInstances = createStatementInstances(listTraces);
                AnalysisLogger.log(true, "Created {} statements", statementInstances);
                BufferedWriter writer = Files.newBufferedWriter(Paths.get(outTranslatedTrace.getAbsolutePath()));
                for (StatementInstance statementInstance : statementInstances) {
                    writer.write(statementInstance.toString());
                    writer.write("\n");
                }
                writer.close();
            }
        } catch (IOException e) {
            AnalysisLogger.warn(true, "Cannot read trace file! {}", e);
        }
    }

    private static List<StatementInstance> createStatementInstances(List<TraceStatement> tr) {
        Chain<SootClass> chain = Scene.v().getApplicationClasses();
        Map<String, SootMethod> allMethods = DynamicControlFlowGraph.createMethodsMap(chain);
        Map<SootMethod, Map<String, Unit>> unitStringsCache = new HashMap<>();
        List<StatementInstance> statementInstances = new ArrayList<>();

        StatementInstance createdStatement;
        int lineNumber = -1;
        for (TraceStatement traceStatement : tr) {
            lineNumber = lineNumber + 1;
            String methodName = traceStatement.getMethod();
            SootMethod mt = allMethods.get(methodName);
            try {
                if (mt.getActiveBody() == null) {
                    continue;
                }
            } catch (Exception ex) {
                // AnalysisLogger.warn(true, "Checking stmt. {}, with method name {}, Exception: {}", traceStatement, methodName, ex);
                continue;
            }
            if (mt.getDeclaringClass().getName().startsWith(Constants.ANDROID_LIBS)) {
                continue;
            }
            Body body = mt.getActiveBody();

            PatchingChain<Unit> units = body.getUnits();

            Map<String, Unit> unitString;
            if (unitStringsCache.containsKey(mt)) {
                unitString = unitStringsCache.get(mt);
            } else {
                unitString = DynamicControlFlowGraph.createUnitStrings(units);
                unitStringsCache.put(mt, unitString);
            }
            createdStatement = createStatementInstance(mt, unitString, traceStatement, lineNumber);
            statementInstances.add(createdStatement);
        }
        return statementInstances;
    }

    private static StatementInstance createStatementInstance(SootMethod mt, Map<String, Unit> unitString, TraceStatement traceStatement, int lineNumber) {
        StatementInstance createdStatement = null;
        if (unitString.containsKey(traceStatement.getInstruction())) {
            createdStatement = matchStatementInstanceToTraceLine(mt, unitString, traceStatement, lineNumber);
        }

        if (createdStatement == null) {
            createdStatement = matchStatementInstanceToClosestTraceLine(mt, unitString, traceStatement, lineNumber);
        }
        return createdStatement;
    }

    private static StatementInstance matchStatementInstanceToTraceLine(SootMethod mt, Map<String, Unit> unitString, TraceStatement traceStatement, int lineNumber) {
        StatementInstance createdStatement = null;
        String us = traceStatement.getInstruction();
        Unit unit = unitString.get(us);
        try {
            createdStatement = new StatementInstance(mt, unit, lineNumber, traceStatement.getThreadId(), traceStatement.getFieldAddr(), unit.getJavaSourceStartLineNumber(), mt.getDeclaringClass().getFilePath());
        } catch (Exception e) {
            AnalysisLogger.error("Cannot create instruction {}", traceStatement);
        }
        return createdStatement;
    }


    private static StatementInstance matchStatementInstanceToClosestTraceLine(SootMethod mt, Map<String, Unit> unitString, TraceStatement traceStatement, int lineNumber) {
        int leastDistance = Integer.MAX_VALUE;
        String second = traceStatement.getInstruction();
        Unit closestUnit = null;
        for (String us : unitString.keySet()) {
            String first = us;
            if (first.contains("if") && first.contains("goto")) {
                first = first.substring(0, first.indexOf("goto"));
            }
            if (second.contains("if") && second.contains("goto")) {
                second = second.substring(0, second.indexOf("goto"));
            }
            if (StringUtils.getCommonPrefix(first, second).length() > 0) {
                int threshold = Math.min(first.length(), second.length()) / 2;
                int distance = (new LevenshteinDistance(threshold)).apply(first, second);
                if (distance == -1) {
                    distance = threshold;
                }
                if (distance < leastDistance) {
                    closestUnit = unitString.get(us);
                    leastDistance = distance;
                }
            }
        }
        StatementInstance createdStatement = null;
        if (closestUnit != null) {
            createdStatement = new StatementInstance(mt, closestUnit, lineNumber, traceStatement.getThreadId(), traceStatement.getFieldAddr(), closestUnit.getJavaSourceStartLineNumber(), mt.getDeclaringClass().getFilePath());
        } else {
            AnalysisLogger.warn(true, "Cannot create instruction {}", traceStatement);
        }
        return createdStatement;
    }

}