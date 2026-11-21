package pl.formbridge.app.web;

import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.formbridge.app.security.TokenService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final ObjectProvider<TokenService> tokens;

    public AuthController(ObjectProvider<TokenService> tokens) {
        this.tokens = tokens;
    }

    public record TokenRequest(@NotBlank String clientId, @NotBlank String clientSecret) {
    }

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> token(@Valid @RequestBody TokenRequest request) {
        TokenService service = tokens.getIfAvailable();
        if (service == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Tryb demo",
                    "Zabezpieczenia są wyłączone (formbridge.security.enabled=false), token nie jest wydawany ani wymagany.");
        }
        return service.issue(request.clientId(), request.clientSecret())
                .map(t -> ResponseEntity.ok(Map.<String, Object>of(
                        "access_token", t.value(),
                        "token_type", "Bearer",
                        "expires_in", t.expiresInSeconds())))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Brak autoryzacji",
                        "Nieprawidłowy identyfikator lub sekret klienta."));
    }
}
