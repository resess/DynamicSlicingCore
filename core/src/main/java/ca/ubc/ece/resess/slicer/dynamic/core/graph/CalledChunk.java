package ca.ubc.ece.resess.slicer.dynamic.core.graph;

import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementInstance;
import ca.ubc.ece.resess.slicer.dynamic.core.statements.StatementMap;
import soot.Type;

public class CalledChunk {
    StatementMap chunk;
    int retLine;
    String retVariable;
    Type retVarType;
    StatementInstance retIu;

    public CalledChunk() {
        chunk = new StatementMap();
        retLine = -1;
    }

    @Override
    public String toString() {
        String sb = "ret variable: " +
                retVariable +
                "\n" +
                "ret type: " +
                retVarType +
                "\n" +
                "chunk: " +
                chunk;
        return sb;
    }

    public StatementMap getChunk() {
        return chunk;
    }

    public StatementInstance getRetIu() {
        return retIu;
    }

    public int getRetLine() {
        return retLine;
    }

    public Type getRetVarType() {
        return retVarType;
    }

    public String getRetVariable() {
        return retVariable;
    }

    public void setRetVariable(String retVariable) {
        this.retVariable = retVariable;
    }

    public void setRetVarType(Type retVarType) {
        this.retVarType = retVarType;
    }

    public void setChunk(StatementMap chunk) {
        this.chunk = chunk;
    }

    public void setRetLine(int retLine) {
        this.retLine = retLine;
    }

    public void setRetIu(StatementInstance retIu) {
        this.retIu = retIu;
    }
}