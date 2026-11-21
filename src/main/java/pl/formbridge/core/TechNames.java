package pl.formbridge.core;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class TechNames {

    private static final int MAX_LENGTH = 60;

    private TechNames() {
    }

    public static String slug(String text) {
        if (text == null) {
            return "pole";
        }
        String s = text.toLowerCase(Locale.ROOT)
                .replace('ł', 'l');
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        s = s.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (s.length() > MAX_LENGTH) {
            s = s.substring(0, MAX_LENGTH).replaceAll("_[^_]*$", "");
        }
        if (s.isEmpty()) {
            s = "pole";
        }
        if (Character.isDigit(s.charAt(0))) {
            s = "p_" + s;
        }
        return s;
    }

    public static final class Registry {
        private final Set<String> used = new HashSet<>();

        public String unique(String text) {
            String base = slug(text);
            String candidate = base;
            int i = 2;
            while (!used.add(candidate)) {
                candidate = base + "_" + i++;
            }
            return candidate;
        }

        public void reserve(String techName) {
            used.add(techName);
        }
    }
}
