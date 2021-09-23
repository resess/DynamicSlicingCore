package ca.ubc.ece.resess.slicer.dynamic.core.graph.sequitur;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Rule {

    // Guard symbol to mark beginning
    // and end of rule.

    public Guard theGuard;

    // Counter keeps track of how many
    // times the rule is used in the
    // grammar.

    public int count;

    // The total number of rules.

    public static int numRules = 0;

    // The rule's number.
    // Used for identification of
    // non-terminals.

    public final int number;

    // Index used for printing.

    public int index;

    public Rule() {
        number = numRules;
        numRules++;
        theGuard = new Guard(this);
        count = 0;
        index = 0;
    }

    public Symbol first() {
        return theGuard.n;
    }

    public Symbol last() {
        return theGuard.p;
    }

    public Map<String, List<String>> getRules() {

        List<Rule> rules = new ArrayList<>(numRules);
        Rule currentRule;
        Rule referredTo;
        Symbol sym;
        int index;
        int processedRules = 0;
        Map<String, List<String>> rulesMap = new HashMap<>();
        rules.add(this);
        while (processedRules < rules.size()) {
            currentRule = rules.get(processedRules);
            String rulesKey = "R" + processedRules;
            List<String> rulesVal = new ArrayList<>();
            for (sym = currentRule.first(); (!sym.isGuard()); sym = sym.n) {
                if (sym.isNonTerminal()) {
                    referredTo = ((NonTerminal) sym).r;
                    if ((rules.size() > referredTo.index) && (rules.get(referredTo.index) == referredTo)) {
                        index = referredTo.index;
                    } else {
                        index = rules.size();
                        referredTo.index = index;
                        rules.add(referredTo);
                    }
                    rulesVal.add("R" + index);
                } else {
                    if (sym.value == ' ') {
                        rulesVal.add("_");
                    } else {
                        if (sym.value == '\n') {
                            rulesVal.add("\\n");
                        } else {
                            rulesVal.add(String.valueOf(sym.value));
                        }
                    }
                }
            }
            rulesMap.put(rulesKey, rulesVal);
            processedRules++;
        }
        return rulesMap;
    }
}