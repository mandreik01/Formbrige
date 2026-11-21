package pl.formbridge.core.model;

import java.util.function.Function;

public sealed interface Condition {

    String toJsonata(Function<String, String> tabOfSection);

    static Condition answerIs(String sectionTech, String fieldTech, String value) {
        return new Answer(sectionTech, fieldTech, value);
    }

    static Condition not(Condition c) {
        return new Not(c);
    }

    static Condition and(Condition a, Condition b) {
        return new And(a, b);
    }

    static Condition or(Condition a, Condition b) {
        return new Or(a, b);
    }

    record Answer(String sectionTech, String fieldTech, String value) implements Condition {
        @Override
        public String toJsonata(Function<String, String> tabOfSection) {
            String tab = tabOfSection.apply(sectionTech);
            return "(\"" + esc(value) + "\" in tabs['" + escSingle(tab) + "' in title].questions['" + sectionTech
                    + "' in field.techName].field.repeatingSectionGrid['" + fieldTech
                    + "' in field.techName].field.__userAnswer)";
        }
    }

    record Not(Condition inner) implements Condition {
        @Override
        public String toJsonata(Function<String, String> tabOfSection) {
            return "$not" + inner.toJsonata(tabOfSection);
        }
    }

    record And(Condition left, Condition right) implements Condition {
        @Override
        public String toJsonata(Function<String, String> tabOfSection) {
            return "(" + left.toJsonata(tabOfSection) + " and " + right.toJsonata(tabOfSection) + ")";
        }
    }

    record Or(Condition left, Condition right) implements Condition {
        @Override
        public String toJsonata(Function<String, String> tabOfSection) {
            return "(" + left.toJsonata(tabOfSection) + " or " + right.toJsonata(tabOfSection) + ")";
        }
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String escSingle(String s) {
        return s.replace("'", "\\'");
    }
}
