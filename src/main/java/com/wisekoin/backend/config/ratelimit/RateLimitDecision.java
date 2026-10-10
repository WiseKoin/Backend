package com.wisekoin.backend.config.ratelimit;

public record RateLimitDecision(
        boolean allowed, int limit, int remaining, long resetEpochSecond, long retryAfterSeconds) {}
