package pl.formbridge.app.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import pl.formbridge.app.config.FormBridgeProperties;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "formbridge.security.enabled", havingValue = "true", matchIfMissing = true)
public class JwtSupport {

    public static final int MIN_SECRET_BYTES = 32;

    @Bean
    SecretKeySpec formBridgeJwtKey(FormBridgeProperties properties) {
        String secret = properties.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("Brak właściwości formbridge.jwt.secret (FORMBRIDGE_JWT_SECRET) "
                    + "albo jest krótsza niż " + MIN_SECRET_BYTES + " bajtów. "
                    + "Ustaw sekret albo wyłącz zabezpieczenia: formbridge.security.enabled=false.");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKeySpec key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    TokenService tokenService(FormBridgeProperties properties, JwtEncoder encoder) {
        return new TokenService(properties, encoder);
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKeySpec key, FormBridgeProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
        return decoder;
    }
}
