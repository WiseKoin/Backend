package com.wisekoin.backend.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RestClientConfigTest {
    private final RestClientConfig config = new RestClientConfig();

    @Test
    void createsClientWhenTimeoutsArePositive() {
        assertNotNull(config.restClient(Duration.ofSeconds(1), Duration.ofSeconds(2)));
    }

    @Test
    void rejectsMissingTimeoutBoundaries() {
        assertThrows(
                IllegalArgumentException.class,
                () -> config.restClient(Duration.ZERO, Duration.ofSeconds(2)));
        assertThrows(
                IllegalArgumentException.class,
                () -> config.restClient(Duration.ofSeconds(1), Duration.ZERO));
    }
}
