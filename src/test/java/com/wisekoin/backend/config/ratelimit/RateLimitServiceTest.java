package com.wisekoin.backend.config.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RateLimitServiceTest {
    @Test
    void rejectsRequestsAfterLimitWithinSameWindow() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-10T00:00:10Z"), ZoneOffset.UTC);
        RateLimitService service = new RateLimitService(clock);

        assertTrue(service.consume("ai:user:7", 2, 60).allowed());
        RateLimitDecision second = service.consume("ai:user:7", 2, 60);
        RateLimitDecision third = service.consume("ai:user:7", 2, 60);

        assertTrue(second.allowed());
        assertEquals(0, second.remaining());
        assertFalse(third.allowed());
        assertEquals(50, third.retryAfterSeconds());
    }

    @Test
    void policiesUseIndependentBuckets() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-10T00:00:10Z"), ZoneOffset.UTC);
        RateLimitService service = new RateLimitService(clock);

        assertTrue(service.consume("ai:user:7", 1, 60).allowed());
        assertFalse(service.consume("ai:user:7", 1, 60).allowed());
        assertTrue(service.consume("api:user:7", 1, 60).allowed());
    }
}
