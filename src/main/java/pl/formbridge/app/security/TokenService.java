package pl.formbridge.app.security;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import pl.formbridge.app.config.FormBridgeProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;

public class TokenService {

    private final FormBridgeProperties properties;
    private final JwtEncoder encoder;

    public TokenService(FormBridgeProperties properties, JwtEncoder encoder) {
        this.properties = properties;
        this.encoder = encoder;
    }

    public Optional<IssuedToken> issue(String clientId, String clientSecret) {
        FormBridgeProperties.Auth auth = properties.auth();
        if (!matches(auth.clientId(), clientId) || !matches(auth.clientSecret(), clientSecret)) {
            return Optional.empty();
        }
        long seconds = properties.jwt().ttl().toSeconds();
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(seconds))
                .subject(clientId)
                .claim("scope", "forms:convert")
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return Optional.of(new IssuedToken(token, seconds));
    }

    private static boolean matches(String expected, String given) {
        if (expected == null || expected.isBlank() || given == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }

    public record IssuedToken(String value, long expiresInSeconds) {
    }
}
