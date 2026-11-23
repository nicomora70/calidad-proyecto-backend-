package co.ritech.common;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResilientCallTest {

    private final ResilientCall call = new ResilientCall("test", 3, Duration.ofMillis(1), Duration.ofMinutes(1));

    static class Unavailable extends RuntimeException {
        Unavailable(Throwable cause) {
            super(cause);
        }
    }

    @Test
    void retriesTransientFailuresUntilSuccess() {
        var attempts = new AtomicInteger();
        String result = call.execute(
                () -> {
                    if (attempts.incrementAndGet() < 3) {
                        throw new ResourceAccessException("connection refused");
                    }
                    return "ok";
                },
                Unavailable::new);

        assertThat(result).isEqualTo("ok");
        assertThat(attempts).hasValue(3);
    }

    @Test
    void mapsExhaustedRetriesToBusinessException() {
        var attempts = new AtomicInteger();
        assertThatThrownBy(() -> call.execute(
                        () -> {
                            attempts.incrementAndGet();
                            throw new ResourceAccessException("timeout");
                        },
                        Unavailable::new))
                .isInstanceOf(Unavailable.class);
        assertThat(attempts).hasValue(3);
    }

    @Test
    void doesNotRetryClientErrors() {
        var attempts = new AtomicInteger();
        assertThatThrownBy(() -> call.execute(
                        () -> {
                            attempts.incrementAndGet();
                            throw HttpClientErrorException.create(
                                    HttpStatus.UNPROCESSABLE_ENTITY, "bad", null, null, null);
                        },
                        Unavailable::new))
                .isInstanceOf(HttpClientErrorException.class);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void opensCircuitAfterRepeatedFailuresAndFailsFast() {
        var attempts = new AtomicInteger();
        for (int i = 0; i < 2; i++) { // 2 calls x 3 attempts = 6 recorded failures >= minimum of 5
            assertThatThrownBy(() -> call.execute(
                            () -> {
                                attempts.incrementAndGet();
                                throw new ResourceAccessException("down");
                            },
                            Unavailable::new))
                    .isInstanceOf(Unavailable.class);
        }
        assertThat(call.state()).isEqualTo(CircuitBreaker.State.OPEN);

        int before = attempts.get();
        assertThatThrownBy(() -> call.execute(
                        () -> {
                            attempts.incrementAndGet();
                            return "never";
                        },
                        Unavailable::new))
                .isInstanceOf(Unavailable.class);
        assertThat(attempts.get()).isEqualTo(before); // dependency not called while open
    }
}
