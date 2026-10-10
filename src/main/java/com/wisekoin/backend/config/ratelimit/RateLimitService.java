package com.wisekoin.backend.config.ratelimit;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {
    private static final long CLEANUP_INTERVAL = 1_024;

    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong requestCount = new AtomicLong();

    public RateLimitService(Clock clock) {
        this.clock = clock;
    }

    public RateLimitDecision consume(String key, int limit, long windowSeconds) {
        long now = clock.instant().getEpochSecond();
        long windowStart = Math.floorDiv(now, windowSeconds) * windowSeconds;
        DecisionHolder holder = new DecisionHolder();

        windows.compute(
                key,
                (ignored, existing) -> {
                    Window current =
                            existing == null || existing.startEpochSecond != windowStart
                                    ? new Window(windowStart, 0)
                                    : existing;
                    current.count++;
                    int remaining = Math.max(0, limit - current.count);
                    long reset = windowStart + windowSeconds;
                    holder.decision =
                            new RateLimitDecision(
                                    current.count <= limit,
                                    limit,
                                    remaining,
                                    reset,
                                    Math.max(1, reset - now));
                    return current;
                });

        if (requestCount.incrementAndGet() % CLEANUP_INTERVAL == 0) {
            windows.entrySet()
                    .removeIf(entry -> entry.getValue().startEpochSecond + windowSeconds <= now);
        }
        return holder.decision;
    }

    private static final class Window {
        private final long startEpochSecond;
        private int count;

        private Window(long startEpochSecond, int count) {
            this.startEpochSecond = startEpochSecond;
            this.count = count;
        }
    }

    private static final class DecisionHolder {
        private RateLimitDecision decision;
    }
}
