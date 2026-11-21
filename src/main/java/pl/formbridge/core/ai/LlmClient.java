package pl.formbridge.core.ai;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.Optional;

public interface LlmClient {

    Optional<String> complete(String system, String user, JsonNode schema, Duration timeout);

    String modelName();
}
