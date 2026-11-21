package pl.formbridge.core;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class Fixtures {

    public static final List<String> ALL = List.of("wniosek-numer-porzadkowy", "wniosek-zwrot-oplaty-skarbowej",
            "wniosek-zaswiadczenie", "wniosek-swietlica", "wniosek-nadplata-podatku", "wniosek-pas-drogowy",
            "wniosek-psy-agresywne", "deklaracja-odpady", "wniosek-stypendium-sportowe");

    private Fixtures() {
    }

    public static ExtractedDocument document(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/forms/" + name + ".txt")) {
            if (in == null) {
                throw new IllegalStateException("Brak pliku testowego " + name);
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new ExtractedDocument(name + ".pdf", "application/pdf", List.of(text.split("\f")), false);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<JsonNode> fields(JsonNode form) {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode tab : form.path("tabs")) {
            for (JsonNode q : tab.path("questions")) {
                out.add(q.path("field"));
                for (JsonNode c : q.path("field").path("repeatingSectionGrid")) {
                    out.add(c.path("field"));
                }
            }
        }
        return out;
    }

    public static JsonNode field(JsonNode form, String techName) {
        return fields(form).stream().filter(f -> techName.equals(f.path("techName").asText())).findFirst()
                .orElseThrow(() -> new AssertionError("Brak pola " + techName));
    }

    public static List<String> titles(JsonNode form, String type) {
        return fields(form).stream().filter(f -> type.equals(f.path("type").asText()))
                .map(f -> f.path("title").asText()).toList();
    }
}
