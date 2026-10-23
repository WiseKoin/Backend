package com.wisekoin.backend.module.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

class AiProviderResilienceTest {
    @Test
    void retriesTransientFailuresWithBoundedExponentialBackoff() {
        AtomicInteger calls = new AtomicInteger();
        List<Duration> sleeps = new ArrayList<>();
        AiProviderResilience resilience =
                resilience(
                        new MutableClock(Instant.EPOCH),
                        3,
                        Duration.ofMillis(100),
                        Duration.ofMillis(150),
                        5,
                        sleeps::add);

        String result =
                resilience.execute(
                        "primary",
                        () -> {
                            if (calls.incrementAndGet() < 3) {
                                throw new ResourceAccessException(
                                        "temporary network failure", new IOException("timeout"));
                            }
                            return "ok";
                        });

        assertEquals("ok", result);
        assertEquals(3, calls.get());
        assertEquals(List.of(Duration.ofMillis(100), Duration.ofMillis(150)), sleeps);
        assertEquals(
                AiProviderResilience.ProviderStatus.CLOSED, resilience.health("primary").status());
    }

    @Test
    void doesNotRetryNonTransientClientErrors() {
        AtomicInteger calls = new AtomicInteger();
        List<Duration> sleeps = new ArrayList<>();
        AiProviderResilience resilience =
                resilience(
                        new MutableClock(Instant.EPOCH),
                        3,
                        Duration.ofMillis(100),
                        Duration.ofSeconds(1),
                        5,
                        sleeps::add);

        assertThrows(
                HttpClientErrorException.class,
                () ->
                        resilience.execute(
                                "primary",
                                () -> {
                                    calls.incrementAndGet();
                                    throw HttpClientErrorException.create(
                                            HttpStatus.BAD_REQUEST,
                                            "bad request",
                                            HttpHeaders.EMPTY,
                                            new byte[0],
                                            null);
                                }));

        assertEquals(1, calls.get());
        assertEquals(List.of(), sleeps);
    }

    @Test
    void retriesRateLimits() {
        AtomicInteger calls = new AtomicInteger();
        AiProviderResilience resilience =
                resilience(
                        new MutableClock(Instant.EPOCH),
                        2,
                        Duration.ofMillis(1),
                        Duration.ofMillis(1),
                        5,
                        ignored -> {});

        String result =
                resilience.execute(
                        "primary",
                        () -> {
                            if (calls.incrementAndGet() == 1) {
                                throw HttpClientErrorException.create(
                                        HttpStatus.TOO_MANY_REQUESTS,
                                        "rate limited",
                                        HttpHeaders.EMPTY,
                                        new byte[0],
                                        null);
                            }
                            return "ok";
                        });

        assertEquals("ok", result);
        assertEquals(2, calls.get());
    }

    @Test
    void opensCircuitAndRecoversThroughOneHalfOpenProbe() {
        MutableClock clock = new MutableClock(Instant.EPOCH);
        AiProviderResilience resilience =
                resilience(clock, 1, Duration.ofMillis(1), Duration.ofMillis(1), 2, ignored -> {});

        assertThrows(
                ResourceAccessException.class,
                () -> resilience.execute("primary", this::transientFailure));
        assertThrows(
                ResourceAccessException.class,
                () -> resilience.execute("primary", this::transientFailure));
        assertEquals(
                AiProviderResilience.ProviderStatus.OPEN, resilience.health("primary").status());

        AtomicInteger rejectedCall = new AtomicInteger();
        assertThrows(
                AiCircuitOpenException.class,
                () ->
                        resilience.execute(
                                "primary",
                                () -> {
                                    rejectedCall.incrementAndGet();
                                    return "not-called";
                                }));
        assertEquals(0, rejectedCall.get());

        clock.advance(Duration.ofSeconds(31));
        assertEquals(
                AiProviderResilience.ProviderStatus.HALF_OPEN,
                resilience.health("primary").status());
        assertEquals("recovered", resilience.execute("primary", () -> "recovered"));
        assertEquals(
                AiProviderResilience.ProviderStatus.CLOSED, resilience.health("primary").status());
    }

    private String transientFailure() {
        throw new ResourceAccessException("temporary network failure", new IOException("timeout"));
    }

    private AiProviderResilience resilience(
            Clock clock,
            int attempts,
            Duration initialBackoff,
            Duration maxBackoff,
            int failureThreshold,
            AiProviderResilience.RetrySleeper sleeper) {
        return new AiProviderResilience(
                clock,
                attempts,
                initialBackoff,
                maxBackoff,
                failureThreshold,
                Duration.ofSeconds(30),
                sleeper);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
