package pl.formbridge.app.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ProblemDetail;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProblemJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProblemJson() {
    }

    public static String write(ProblemDetail problem) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", problem.getType() == null ? "about:blank" : problem.getType().toString());
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        body.put("detail", problem.getDetail());
        if (problem.getInstance() != null) {
            body.put("instance", problem.getInstance().toString());
        }
        return serialize(body);
    }

    public static String quote(String value) {
        return serialize(value);
    }

    public static String array(List<String> values) {
        return serialize(values == null ? List.of() : values);
    }

    private static String serialize(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Nie udało się zapisać JSON", e);
        }
    }
}
