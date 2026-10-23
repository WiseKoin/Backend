package com.wisekoin.backend.module.ai.provider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class AiProviderResilience {
    private static final int MAX_CONFIGURED_ATTEMPTS = 5;

    private final Clock clock;
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    private final int circuitFailureThreshold;
    private final Duration circuitOpenDuration;
    private final RetrySleeper retrySleeper;
    private final Map<String, CircuitState> circuits = new ConcurrentHashMap<>();

    @Autowired
    public AiProviderResilience(
            Clock clock,
            @Value("${app.ai.provider.retry.max-attempts:3}") int maxAttempts,
            @Value("${app.ai.provider.retry.initial-backoff:200ms}") Duration initialBackoff,
            @Value("${app.ai.provider.retry.max-backoff:2s}") Duration maxBackoff,
            @Value("${app.ai.provider.circuit.failure-threshold:5}") int circuitFailureThreshold,
            @Value("${app.ai.provider.circuit.open-duration:30s}") Duration circuitOpenDuration) {
        this(
                clock,
                maxAttempts,
                initialBackoff,
                maxBackoff,
                circuitFailureThreshold,
                circuitOpenDuration,
                Thread::sleep);
    }

    AiProviderResilience(
            Clock clock,
            int maxAttempts,
            Duration initialBackoff,
            Duration maxBackoff,
            int circuitFailureThreshold,
            Duration circuitOpenDuration,
            RetrySleeper retrySleeper) {
        if (maxAttempts < 1 || maxAttempts > MAX_CONFIGURED_ATTEMPTS) {
            throw new IllegalArgumentException("AI retry max attempts must be between 1 and 5.");
        }
        requirePositive(initialBackoff, "AI retry initial backoff");
        requirePositive(maxBackoff, "AI retry maximum backoff");
        if (initialBackoff.compareTo(maxBackoff) > 0) {
            throw new IllegalArgumentException(
                    "AI retry initial backoff cannot exceed maximum backoff.");
        }
        if (circuitFailureThreshold < 1) {
            throw new IllegalArgumentException("AI circuit failure threshold must be positive.");
        }
        requirePositive(circuitOpenDuration, "AI circuit open duration");

        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
        this.circuitFailureThreshold = circuitFailureThreshold;
        this.circuitOpenDuration = circuitOpenDuration;
        this.retrySleeper = retrySleeper;
    }

    public <T> T execute(String providerKey, Supplier<T> providerCall) {
        CircuitState circuit = circuits.computeIfAbsent(providerKey, ignored -> new CircuitState());
        boolean halfOpenProbe = circuit.acquire(clock.instant(), circuitOpenDuration);
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                T result = providerCall.get();
                circuit.recordSuccess();
                return result;
            } catch (RuntimeException failure) {
                lastFailure = failure;
                if (!isRetryable(failure) || attempt == maxAttempts) {
                    break;
                }
                sleep(backoffFor(attempt), circuit, halfOpenProbe, failure);
            }
        }

        circuit.recordFailure(
                clock.instant(), circuitFailureThreshold, circuitOpenDuration, halfOpenProbe);
        throw lastFailure;
    }

    public ProviderHealth health(String providerKey) {
        CircuitState circuit = circuits.get(providerKey);
        if (circuit == null) {
            return new ProviderHealth(ProviderStatus.CLOSED, 0, null);
        }
        return circuit.health(clock.instant());
    }

    boolean isRetryable(Throwable failure) {
        if (failure instanceof ResourceAccessException) {
            return true;
        }
        if (failure instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();
            return status == 429 || status >= 500;
        }
        return false;
    }

    private void sleep(
            Duration duration,
            CircuitState circuit,
            boolean halfOpenProbe,
            RuntimeException failure) {
        try {
            retrySleeper.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            circuit.recordFailure(
                    clock.instant(), circuitFailureThreshold, circuitOpenDuration, halfOpenProbe);
            throw new AiProviderRetryInterruptedException(exception, failure);
        }
    }

    private Duration backoffFor(int completedAttempt) {
        long multiplier = 1L << Math.min(completedAttempt - 1, 30);
        Duration candidate;
        try {
            candidate = initialBackoff.multipliedBy(multiplier);
        } catch (ArithmeticException exception) {
            return maxBackoff;
        }
        return candidate.compareTo(maxBackoff) > 0 ? maxBackoff : candidate;
    }

    private static void requirePositive(Duration value, String name) {
        if (value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException(name + " must be positive.");
        }
    }

    public enum ProviderStatus {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    public record ProviderHealth(ProviderStatus status, int consecutiveFailures, Instant retryAt) {}

    @FunctionalInterface
    interface RetrySleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private static final class CircuitState {
        private int consecutiveFailures;
        private Instant openUntil;
        private boolean halfOpenProbeInProgress;

        synchronized boolean acquire(Instant now, Duration openDuration) {
            if (openUntil == null) {
                return false;
            }
            if (now.isBefore(openUntil)) {
                throw new AiCircuitOpenException();
            }
            if (halfOpenProbeInProgress) {
                throw new AiCircuitOpenException();
            }
            halfOpenProbeInProgress = true;
            openUntil = now.plus(openDuration);
            return true;
        }

        synchronized void recordSuccess() {
            consecutiveFailures = 0;
            openUntil = null;
            halfOpenProbeInProgress = false;
        }

        synchronized void recordFailure(
                Instant now, int failureThreshold, Duration openDuration, boolean halfOpenProbe) {
            consecutiveFailures++;
            if (halfOpenProbe || consecutiveFailures >= failureThreshold) {
                openUntil = now.plus(openDuration);
            }
            halfOpenProbeInProgress = false;
        }

        synchronized ProviderHealth health(Instant now) {
            if (openUntil == null) {
                return new ProviderHealth(ProviderStatus.CLOSED, consecutiveFailures, null);
            }
            if (halfOpenProbeInProgress) {
                return new ProviderHealth(ProviderStatus.HALF_OPEN, consecutiveFailures, openUntil);
            }
            ProviderStatus status =
                    now.isBefore(openUntil) ? ProviderStatus.OPEN : ProviderStatus.HALF_OPEN;
            return new ProviderHealth(status, consecutiveFailures, openUntil);
        }
    }

    private static final class AiProviderRetryInterruptedException
            extends AiProviderResilienceException {
        private AiProviderRetryInterruptedException(
                InterruptedException interruption, RuntimeException providerFailure) {
            super("AI provider retry was interrupted.", interruption);
            addSuppressed(providerFailure);
        }
    }
}
