package pl.formbridge.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import pl.formbridge.core.Fixtures;
import pl.formbridge.core.FormBridgeConverter;
import pl.formbridge.core.FormRules;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormRefinerTest {

    private static LlmClient fake(Function<List<String>, String> answer) {
        return new LlmClient() {
            @Override
            public Optional<String> complete(String system, String user, JsonNode schema, Duration timeout) {
                List<String> ids = new ArrayList<>();
                for (String line : user.split("\n")) {
                    if (line.matches("^[fs]\\d+ \\|.*")) {
                        ids.add(line.substring(0, line.indexOf(' ')));
                    }
                }
                return Optional.ofNullable(answer.apply(ids));
            }

            @Override
            public String modelName() {
                return "atrapa";
            }
        };
    }

    private static String items(List<String> ids, String type) {
        StringBuilder b = new StringBuilder("{\"items\":[");
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            b.append(i > 0 ? "," : "").append("{\"id\":\"").append(id).append("\",\"label\":\"Poprawiona ").append(id)
                    .append("\",\"type\":\"").append(id.startsWith("f") ? type : "").append("\"}");
        }
        return b.append("]}").toString();
    }

    @Test
    void appliesModelLabelsAndKeepsFormValid() throws Exception {
        var converter = new FormBridgeConverter(new FormRefiner(fake(ids -> items(ids, "TEXT_FIELD")), Duration.ofSeconds(10), 40, 8));
        var result = converter.convertDetailed(Fixtures.document("wniosek-pas-drogowy"));
        assertTrue(result.ai().refined() > 0);
        assertEquals(result.ai().candidates(), result.ai().refined());
        assertTrue(result.json().contains("Poprawiona"));
        assertTrue(FormRules.check(new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.json())).isEmpty());
    }

    @Test
    void invalidAnswerFallsBackToRules() throws Exception {
        var converter = new FormBridgeConverter(new FormRefiner(fake(ids -> "to nie jest JSON"), Duration.ofSeconds(10), 40, 8));
        var result = converter.convertDetailed(Fixtures.document("wniosek-pas-drogowy"));
        assertEquals(0, result.ai().refined());
        assertFalse(result.json().contains("Poprawiona"));
    }

    @Test
    void unknownTypeIsIgnored() throws Exception {
        var converter = new FormBridgeConverter(new FormRefiner(fake(ids -> items(ids, "SECTION")), Duration.ofSeconds(10), 40, 8));
        var result = converter.convertDetailed(Fixtures.document("wniosek-pas-drogowy"));
        assertTrue(FormRules.check(new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.json())).isEmpty());
    }

    @Test
    void unreachableServerDoesNotBreakConversion() throws Exception {
        var converter = FormBridgeConverter.withLocalLlm(List.of(new LlamaCppClient.Endpoint("http://127.0.0.1:9", "brak")),
                Duration.ofSeconds(3));
        long start = System.nanoTime();
        var result = converter.convertDetailed(Fixtures.document("wniosek-zaswiadczenie"));
        assertEquals(0, result.ai().refined());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 5);
    }

    @Test
    void weakLabelDetection() {
        assertTrue(FormRefiner.isWeak("Przy ulicy1)"));
        assertTrue(FormRefiner.isWeak("Uzupełnij"));
        assertTrue(FormRefiner.isWeak("Tj"));
        assertFalse(FormRefiner.isWeak("Data urodzenia psa"));
    }
}
