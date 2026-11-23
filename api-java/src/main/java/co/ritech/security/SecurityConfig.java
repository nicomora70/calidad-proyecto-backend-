package co.ritech.security;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import co.ritech.common.RequestIdFilter;
import co.ritech.config.RitechProperties;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
        "/api/v1/health",
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/logout",
        "/livez",
        "/readyz",
        "/swagger-ui.html",
        "/swagger-ui/**",
        "/v3/api-docs",
        "/v3/api-docs.yaml",
        "/v3/api-docs/**"
    };

    private static final Set<String> PROBES = Set.of("/livez", "/readyz", "/actuator/health");

    /** API answers JSON only; Swagger UI (same origin) needs its own scripts, styles and data: images. */
    private static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; object-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'";

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, ObjectMapper mapper, JwtAuthenticationConverter converter, RitechProperties properties)
            throws Exception {
        if (properties.security().requireHttps()) {
            // Behind the proxy, X-Forwarded-Proto decides (server.forward-headers-strategy=framework).
            // Probes stay on plain HTTP for the container healthcheck.
            http.redirectToHttps(https -> https.requestMatchers(request -> !PROBES.contains(request.getRequestURI())));
        }
        http.csrf(csrf -> csrf.disable()) // stateless API, bearer tokens only (no cookies)
                .cors(cors -> {})
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(ref -> ref.policy(ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(
                                hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                        .addHeaderWriter(new StaticHeadersWriter(
                                "Permissions-Policy", "camera=(), microphone=(), geolocation=()")))
                .addFilterAfter(
                        new RateLimitFilter(properties.rateLimit(), mapper), BearerTokenAuthenticationFilter.class)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                        .permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS)
                        .permitAll()
                        // Management port (MANAGEMENT_PORT, internal network only; not published)
                        .requestMatchers("/actuator/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/settings/branding")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth -> oauth.bearerTokenResolver(publicEndpointsIgnoreToken())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(ProblemResponses.entryPoint(mapper))
                        .accessDeniedHandler(ProblemResponses.accessDenied(mapper)))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(ProblemResponses.entryPoint(mapper))
                        .accessDeniedHandler(ProblemResponses.accessDenied(mapper)));
        return http.build();
    }

    /**
     * A stale access token sent to a public endpoint (typically /api/v1/auth/refresh)
     * must not cause a 401: the token is simply ignored there.
     */
    private static BearerTokenResolver publicEndpointsIgnoreToken() {
        var delegate = new DefaultBearerTokenResolver();
        var matcher = new AntPathMatcher();
        return request -> {
            String path = request.getRequestURI();
            for (String pattern : PUBLIC_ENDPOINTS) {
                if (matcher.match(pattern, path)) {
                    return null;
                }
            }
            return delegate.resolve(request);
        };
    }

    /** "role" claim -> ROLE_ADMIN / ROLE_IT_MANAGER / ROLE_INVESTOR authorities. */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(CurrentUser.CLAIM_ROLE);
        authorities.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public SecretKey jwtSigningKey(RitechProperties properties) {
        String secret = properties.security().jwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "ritech.security.jwt-secret (JWT_SECRET) must be set and be at least 32 bytes long");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey key, RitechProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(
                JwtValidators.createDefaultWithIssuer(properties.security().jwtIssuer()));
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(RitechProperties properties) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(properties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", RequestIdFilter.HEADER));
        config.setExposedHeaders(List.of(
                RequestIdFilter.HEADER,
                "Content-Disposition",
                "Retry-After",
                "X-RateLimit-Limit",
                "X-RateLimit-Remaining"));
        config.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", config);
        return source;
    }
}
