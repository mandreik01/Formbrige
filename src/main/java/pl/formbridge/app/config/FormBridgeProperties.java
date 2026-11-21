package pl.formbridge.app.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "formbridge")
public record FormBridgeProperties(
        @Valid @DefaultValue Upload upload,
        @Valid @DefaultValue Batch batch,
        @Valid @DefaultValue Security security,
        @Valid @DefaultValue Jwt jwt,
        @Valid @DefaultValue Auth auth,
        @Valid @DefaultValue Ocr ocr,
        @Valid @DefaultValue Llm llm) {

    public record Upload(@NotNull @DefaultValue("20MB") DataSize maxFileSize) {
    }

    public record Batch(@Min(1) @DefaultValue("20") int maxFiles) {
    }

    public record Security(@DefaultValue("true") boolean enabled) {
    }

    public record Jwt(String secret,
                      @NotNull @DefaultValue("1h") Duration ttl,
                      @DefaultValue("formbridge") String issuer) {
    }

    public record Auth(String clientId, String clientSecret) {
    }

    public record Ocr(@DefaultValue("pol") String language,
                      @NotNull @DefaultValue("60s") Duration timeout) {
    }

    public record Llm(@DefaultValue("true") boolean enabled,
                      @DefaultValue("http://llm-pllum:8080=PLLuM-4B-instruct-2512,http://llm-bielik:8080=Bielik-1.5B-v3.0-Instruct")
                      List<String> endpoints,
                      @NotNull @DefaultValue("45s") Duration timeout) {
    }
}
