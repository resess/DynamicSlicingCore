package ca.ubc.ece.resess.slicer.dynamic.core.statements;

import ca.ubc.ece.resess.slicer.dynamic.core.accesspath.AccessPath;
import ca.ubc.ece.resess.slicer.dynamic.core.accesspath.AliasSet;
import soot.toolkits.scalar.Pair;

import java.util.*;

public class StatementVarSet extends LinkedHashMap<StatementInstance, AliasSet> {
    private static final long serialVersionUID = 1L;

    public StatementVarSet() {
        super();
    }

//    public StatementVarSet(StatementVarSet other) {
//        this.addAll(other);
//    }

//    public StatementVarSet(StatementInstance... statements) {
//        this.addAll(Arrays.asList(statements));
//    }

    public boolean add(StatementInstance e, AccessPath p) {
        synchronized (this) {
            if(super.containsKey(e)){
                return super.get(e).add(p) ;
            }
            return super.put(e, new AliasSet(p)) == null;
        }
    }

//    public StatementVarSet subtract(StatementVarSet other) {
//        StatementVarSet ret = new StatementVarSet();
//        for (StatementInstance iu : this) {
//            if (!other.contains(iu)) {
//                ret.add(iu);
//            }
//        }
//        return ret;
//    }

//    public StatementVarSet reorder() {
//        StatementVarSet ordered = new StatementVarSet();
//        List<StatementInstance> orderedList = new ArrayList<>();
//        orderedList.addAll(this);
//        Collections.sort(orderedList, (lhs, rhs) -> {
//            if (rhs.getLineNo() > lhs.getLineNo()) {
//                return 1;
//            } else if (rhs.getLineNo() < lhs.getLineNo()) {
//                return -1;
//            }
//            return 0;
//        });
//        for (StatementInstance iu: orderedList) {
//            if (!ordered.contains(iu)) {
//                ordered.add(iu);
//            }
//        }
//        return ordered;
//    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (StatementInstance iu: this.keySet()) {
            sb.append("\n");
            sb.append("    ");
            sb.append(iu);
        }
        return sb.toString();
    }
}