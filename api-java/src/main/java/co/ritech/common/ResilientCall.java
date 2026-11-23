package co.ritech.common;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

/**
 * Retry + circuit breaker around a call to an external dependency.
 *
 * Only transient failures are retried and counted by the breaker: network errors
 * (timeouts, connection refused) and HTTP 5xx. A 4xx means our request is wrong,
 * so retrying it would not help. While the breaker is open, calls fail fast
 * without touching the dependency, and the failure is mapped to the caller's
 * business exception.
 */
public final class ResilientCall {

    public static final Predicate<Throwable> TRANSIENT =
            ex -> ex instanceof ResourceAccessException || ex instanceof HttpServerErrorException;

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public ResilientCall(String name, int maxAttempts, Duration initialBackoff, Duration openStateWait) {
        this.circuitBreaker = CircuitBreaker.of(
                name,
                CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(openStateWait)
                        .permittedNumberOfCallsInHalfOpenState(2)
                        .recordException(TRANSIENT)
                        .build());
        this.retry = Retry.of(
                name,
                RetryConfig.custom()
                        .maxAttempts(maxAttempts)
                        .intervalFunction(IntervalFunction.ofExponentialBackoff(initialBackoff, 2.0))
                        .retryOnException(TRANSIENT)
                        .build());
    }

    /** Defaults used in production: 3 attempts (200 ms, 400 ms backoff), 30 s open state. */
    public static ResilientCall withDefaults(String name) {
        return new ResilientCall(name, 3, Duration.ofMillis(200), Duration.ofSeconds(30));
    }

    /**
     * @param onFailure maps the final failure (or "circuit open") to a business exception
     */
    public <T> T execute(Supplier<T> call, Function<Throwable, ? extends RuntimeException> onFailure) {
        Supplier<T> guarded = Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, call));
        try {
            return guarded.get();
        } catch (CallNotPermittedException ex) {
            throw onFailure.apply(ex);
        } catch (RuntimeException ex) {
            if (TRANSIENT.test(ex)) {
                throw onFailure.apply(ex);
            }
            throw ex;
        }
    }

    public CircuitBreaker.State state() {
        return circuitBreaker.getState();
    }
}
