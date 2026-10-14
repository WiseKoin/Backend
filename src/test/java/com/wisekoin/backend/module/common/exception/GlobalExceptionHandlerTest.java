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

    @Test
    void insufficientBalanceReturnsStableErrorCode() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<Map<String, String>> response =
                handler.handleInsufficientBalance(
                        new InsufficientBalanceException("Insufficient balance in the wallet"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("INSUFFICIENT_BALANCE", response.getBody().get("code"));
        assertEquals("Insufficient balance in the wallet", response.getBody().get("error"));
    }

    @Test
    void unexpectedFailuresDoNotExposeInternalDetails() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<Map<String, String>> response =
                handler.handleGeneric(
                        new RuntimeException("SQL constraint users_password leaked-secret"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(
                Map.of("error", "An unexpected error occurred. Please try again later."),
                response.getBody());
    }
}
