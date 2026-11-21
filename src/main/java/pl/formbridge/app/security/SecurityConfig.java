package pl.formbridge.app.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import pl.formbridge.app.web.ProblemJson;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/", "/index.html", "/favicon.ico", "/assets/**", "/css/**", "/js/**",
            "/api/v1/auth/token", "/actuator/health", "/actuator/health/**"
    };

    @Bean
    @ConditionalOnProperty(name = "formbridge.security.enabled", havingValue = "true", matchIfMissing = true)
    SecurityFilterChain secured(HttpSecurity http, AuthenticationEntryPoint entryPoint) throws Exception {
        return common(http)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .requestMatchers("/api/v1/forms/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()).authenticationEntryPoint(entryPoint))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "formbridge.security.enabled", havingValue = "false")
    SecurityFilterChain open(HttpSecurity http) throws Exception {
        return common(http).authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
    }

    private static HttpSecurity common(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .httpBasic(h -> h.disable())
                .formLogin(f -> f.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    }

    @Bean
    AuthenticationEntryPoint problemDetailAuthenticationEntryPoint() {
        return (request, response, exception) -> {
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
            problem.setType(URI.create("https://formbridge.pl/problem/unauthorized"));
            problem.setTitle("Brak autoryzacji");
            problem.setDetail("Wymagany jest poprawny token JWT w nagłówku Authorization: Bearer <token>.");
            problem.setInstance(URI.create(request.getRequestURI()));
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(ProblemJson.write(problem));
        };
    }
}
