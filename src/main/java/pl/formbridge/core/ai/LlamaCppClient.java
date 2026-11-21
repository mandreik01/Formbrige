package pl.formbridge.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LlamaCppClient implements LlmClient {

    private static final Logger LOG = Logger.getLogger(LlamaCppClient.class.getName());

    private final List<Endpoint> endpoints;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;
    private volatile String lastModel = "";

    public record Endpoint(String baseUrl, String model) {
    }

    public LlamaCppClient(List<Endpoint> endpoints) {
        if (endpoints == null || endpoints.isEmpty()) {
            throw new IllegalArgumentException("Brak adresu serwera modelu językowego");
        }
        this.endpoints = List.copyOf(endpoints);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public Optional<String> complete(String system, String user, JsonNode schema, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        for (Endpoint e : endpoints) {
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                return Optional.empty();
            }
            Optional<String> r = call(e, system, user, schema, Duration.ofNanos(left));
            if (r.isPresent()) {
                lastModel = e.model();
                return r;
            }
        }
        return Optional.empty();
    }

    private Optional<String> call(Endpoint e, String system, String user, JsonNode schema, Duration timeout) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", e.model());
            body.put("temperature", 0.0);
            body.put("top_p", 1.0);
            body.put("max_tokens", 1024);
            body.put("stream", false);
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", system);
            messages.addObject().put("role", "user").put("content", user);
            ObjectNode format = body.putObject("response_format");
            format.put("type", "json_object");
            format.set("schema", schema);

            HttpRequest req = HttpRequest.newBuilder(URI.create(stripSlash(e.baseUrl()) + "/v1/chat/completions"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                LOG.warning(() -> "Serwer modelu " + e.baseUrl() + " zwrócił HTTP " + resp.statusCode());
                return Optional.empty();
            }
            JsonNode content = mapper.readTree(resp.body()).path("choices").path(0).path("message").path("content");
            return content.isTextual() && !content.asText().isBlank() ? Optional.of(content.asText()) : Optional.empty();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Model językowy niedostępny (" + e.baseUrl() + "): " + ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public String modelName() {
        return lastModel.isEmpty() ? endpoints.get(0).model() : lastModel;
    }

    private static String stripSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
