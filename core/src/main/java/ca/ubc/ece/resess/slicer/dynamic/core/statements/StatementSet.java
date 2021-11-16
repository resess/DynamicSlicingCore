package ca.ubc.ece.resess.slicer.dynamic.core.statements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public class StatementSet extends LinkedHashSet<StatementInstance> {
    private static final long serialVersionUID = 1L;

    public StatementSet() {
        super();
    }

    public StatementSet(StatementSet other) {
        this.addAll(other);
    }

    public StatementSet(StatementInstance... statements) {
        this.addAll(Arrays.asList(statements));
    }

    @Override
    public boolean add(StatementInstance e) {
        synchronized (this) {
            return super.add(e);
        }
    }

    public StatementSet subtract(StatementSet other) {
        StatementSet ret = new StatementSet();
        for (StatementInstance iu : this) {
            if (!other.contains(iu)) {
                ret.add(iu);
            }
        }
        return ret;
    }

    public StatementSet reorder() {
        StatementSet ordered = new StatementSet();
        List<StatementInstance> orderedList = new ArrayList<>(this);
        orderedList.sort((lhs, rhs) -> {
            if (rhs.getLineNo() > lhs.getLineNo()) {
                return 1;
            } else if (rhs.getLineNo() < lhs.getLineNo()) {
                return -1;
            }
            return 0;
        });
        for (StatementInstance iu : orderedList) {
            if (!ordered.contains(iu)) {
                ordered.add(iu);
            }
        }
        return ordered;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (StatementInstance iu : this) {
            sb.append("\n");
            sb.append("    ");
            sb.append(iu);
        }
        return sb.toString();
    }
}