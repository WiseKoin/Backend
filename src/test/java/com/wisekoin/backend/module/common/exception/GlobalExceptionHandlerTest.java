package com.wisekoin.backend.module.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {
    @Test
    void badCredentialsReturnsStructuredUnauthorizedResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<Map<String, String>> response =
                handler.handleBadCredentials(new BadCredentialsException("Invalid credentials"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(Map.of("error", "Invalid credentials"), response.getBody());
    }
}
