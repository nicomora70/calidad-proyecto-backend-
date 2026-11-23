package co.ritech.security;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import co.ritech.config.RitechProperties;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Token-bucket rate limiting (in memory, one bucket per key):
 * login/refresh per client IP (strictest, anti brute force), authenticated
 * calls per user, other anonymous calls per IP. Runs after the bearer token
 * filter so the user is known. Exceeding the limit returns 429 RATE_LIMITED.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    static final Set<String> AUTH_PATHS = Set.of("/api/v1/auth/login", "/api/v1/auth/refresh");

    private final RitechProperties.RateLimit limits;
    private final ObjectMapper mapper;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    public RateLimitFilter(RitechProperties.RateLimit limits, ObjectMapper mapper) {
        this.limits = limits;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !limits.enabled()
                || "OPTIONS".equals(request.getMethod())
                || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key;
        int perMinute;
        // remoteAddr already honours X-Forwarded-For (server.forward-headers-strategy=framework)
        String ip = request.getRemoteAddr();
        if (AUTH_PATHS.contains(request.getRequestURI())) {
            key = "auth:" + ip;
            perMinute = limits.authPerMinute();
        } else if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
            key = "user:" + jwt.getName();
            perMinute = limits.userPerMinute();
        } else {
            key = "ip:" + ip;
            perMinute = limits.anonymousPerMinute();
        }

        Bucket bucket = buckets.get(key, k -> newBucket(perMinute));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        response.setHeader("X-RateLimit-Limit", String.valueOf(perMinute));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfter = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        ProblemResponses.write(
                mapper,
                response,
                HttpStatus.TOO_MANY_REQUESTS,
                "RATE_LIMITED",
                "Too many requests, retry in " + retryAfter + " s");
    }

    private static Bucket newBucket(int perMinute) {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)))
                .build();
    }
}
