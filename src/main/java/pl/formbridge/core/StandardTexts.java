package pl.formbridge.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;

final class StandardTexts {

    private static final Map<String, String> TEXTS = load();

    private StandardTexts() {
    }

    static String get(String key) {
        String v = TEXTS.get(key);
        if (v == null) {
            throw new IllegalStateException("Brak tekstu standardowego: " + key);
        }
        return v;
    }

    static String html(String plain) {
        return "<html><head></head><body><p>" + escape(plain) + "</p></body></html>";
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static Map<String, String> load() {
        try (InputStream in = StandardTexts.class.getResourceAsStream("/core/standard-texts.json")) {
            if (in == null) {
                throw new IllegalStateException("Brak zasobu /core/standard-texts.json");
            }
            return new ObjectMapper().readValue(in, new TypeReference<Map<String, String>>() { });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
