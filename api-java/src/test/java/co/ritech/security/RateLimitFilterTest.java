package co.ritech.security;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.fasterxml.jackson.databind.ObjectMapper;

import co.ritech.config.RitechProperties;

import static org.assertj.core.api.Assertions.assertThat;

/** Partitions: auth path per IP, authenticated per user, anonymous per IP, disabled, outside /api. */
class RateLimitFilterTest {

    private final RateLimitFilter filter =
            new RateLimitFilter(new RitechProperties.RateLimit(true, 3, 5, 10), new ObjectMapper());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginAllowsExactlyTheLimitPerIpThenReturns429() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(call("POST", "/api/v1/auth/login", "10.0.0.1").getStatus())
                    .isEqualTo(200);
        }
        MockHttpServletResponse blocked = call("POST", "/api/v1/auth/login", "10.0.0.1");

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("\"code\":\"RATE_LIMITED\"");
        assertThat(Long.parseLong(blocked.getHeader("Retry-After"))).isPositive();
        assertThat(blocked.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        // another client IP has its own bucket
        assertThat(call("POST", "/api/v1/auth/login", "10.0.0.2").getStatus()).isEqualTo(200);
    }

    @Test
    void authenticatedUsersAreLimitedPerUserNotPerIp() throws Exception {
        authenticate("user-a");
        for (int i = 0; i < 10; i++) {
            assertThat(call("GET", "/api/v1/me", "10.0.0." + i).getStatus()).isEqualTo(200);
        }
        assertThat(call("GET", "/api/v1/me", "10.0.0.99").getStatus()).isEqualTo(429);

        authenticate("user-b");
        assertThat(call("GET", "/api/v1/me", "10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void anonymousCallsUseTheIpBucket() throws Exception {
        for (int i = 0; i < 5; i++) {
            call("GET", "/api/v1/settings/branding", "10.0.0.3");
        }
        assertThat(call("GET", "/api/v1/settings/branding", "10.0.0.3").getStatus())
                .isEqualTo(429);
    }

    @Test
    void preflightAndNonApiPathsAreNotLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(call("OPTIONS", "/api/v1/auth/login", "10.0.0.4").getStatus())
                    .isEqualTo(200);
            assertThat(call("GET", "/readyz", "10.0.0.4").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void disabledFilterLetsEverythingThrough() throws Exception {
        var off = new RateLimitFilter(new RitechProperties.RateLimit(false, 1, 1, 1), new ObjectMapper());
        for (int i = 0; i < 3; i++) {
            var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            var response = new MockHttpServletResponse();
            off.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletResponse call(String method, String path, String ip) throws Exception {
        var request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(ip);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private static void authenticate(String subject) {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .subject(subject)
                .claim("role", "INVESTOR")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }
}
