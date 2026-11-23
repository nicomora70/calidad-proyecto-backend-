package co.ritech.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import co.ritech.TestFixtures;
import co.ritech.config.RitechProperties;
import co.ritech.user.Role;
import co.ritech.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final SecurityConfig config = new SecurityConfig();
    private final RitechProperties properties = TestFixtures.properties();
    private final javax.crypto.SecretKey key = config.jwtSigningKey(properties);
    private final JwtDecoder decoder = config.jwtDecoder(key, properties);

    private JwtService serviceAt(Instant instant) {
        return new JwtService(config.jwtEncoder(key), properties, Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void issuedTokenCarriesIdentityAndRoleAndIsAcceptedByDecoder() {
        User user = new User("Ana", "ana@example.com", Role.INVESTOR, "hash", true, Instant.now());
        JwtService service = serviceAt(Instant.now());

        CurrentUser current = CurrentUser.from(decoder.decode(service.issueAccessToken(user)));

        assertThat(current.id()).isEqualTo(user.getId());
        assertThat(current.email()).isEqualTo("ana@example.com");
        assertThat(current.role()).isEqualTo(Role.INVESTOR);
        assertThat(current.mustChangePassword()).isTrue();
        assertThat(service.accessTokenTtlSeconds()).isEqualTo(900);
    }

    @Test
    void expiredTokenIsRejected() {
        User user = new User("Ana", "ana@example.com", Role.INVESTOR, "hash", false, Instant.now());
        String token = serviceAt(Instant.now().minusSeconds(3600)).issueAccessToken(user);

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void shortSecretPreventsStartup() {
        var weak = new RitechProperties(
                properties.simulation(),
                properties.market(),
                properties.calcEngine(),
                properties.cors(),
                new RitechProperties.Security(
                        "too-short",
                        "ritech-api",
                        properties.security().accessTokenTtl(),
                        properties.security().refreshTokenTtl(),
                        5,
                        properties.security().lockDuration(),
                        "",
                        "",
                        "Administrator"),
                properties.ai(),
                properties.news(),
                properties.timezone());

        assertThatThrownBy(() -> config.jwtSigningKey(weak)).isInstanceOf(IllegalStateException.class);
    }
}
