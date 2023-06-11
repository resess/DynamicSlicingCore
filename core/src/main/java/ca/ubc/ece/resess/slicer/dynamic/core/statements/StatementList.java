package ca.ubc.ece.resess.slicer.dynamic.core.statements;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class StatementList extends ArrayList<StatementInstance> {

    private static final long serialVersionUID = 1L;
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Instruction-list: \n");
        for (StatementInstance iu: this) {
            sb.append("    ");
            sb.append(iu.toString());
            sb.append("\n");
        }
        return sb.toString();
    }

    public int getClosestStatementIndex(StatementInstance toFind){
        if (this.size() == 0) {
            return -1;
        }
        Comparator<StatementInstance> comparator = (StatementInstance s1, StatementInstance s2) -> Integer.compare(s1.getLineNo(), s2.getLineNo());
        int pos = Collections.binarySearch(this, toFind, comparator);
        if(pos > 0){
            return pos-1;
        } else if (pos == 0){
            return -1;
        } else if(pos == -1){
            return 0;
        }
        int indexPos = -(pos+2);
        return indexPos;
    }
    public StatementInstance getClosestStatement(StatementInstance toFind){
        int index = getClosestStatementIndex(toFind);
        if(index == -1){
            return null;
        }
        return this.get(index);
    }
}