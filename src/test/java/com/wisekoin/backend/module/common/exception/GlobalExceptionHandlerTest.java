package com.wisekoin.backend.module.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mockStatic;

import io.sentry.Sentry;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
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
        RuntimeException exception =
                new RuntimeException("SQL constraint users_password leaked-secret");

        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            ResponseEntity<Map<String, String>> response = handler.handleGeneric(exception);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals(
                    Map.of("error", "An unexpected error occurred. Please try again later."),
                    response.getBody());
            sentry.verify(() -> Sentry.captureException(exception));
        }
    }

    @Test
    void aiProviderFailureHasStableCodeAndDoesNotExposeCause() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<Map<String, String>> response =
                handler.handleAiProviderUnavailable(
                        new AiProviderUnavailableException(
                                "AI extraction is temporarily unavailable. Please try again later.",
                                new RuntimeException("provider-secret-payload")));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("AI_PROVIDER_UNAVAILABLE", response.getBody().get("code"));
        assertFalse(response.getBody().toString().contains("provider-secret-payload"));
    }

    @Test
    void mediaIoFailureHasStableSafeResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        IOException exception = new IOException("temporary-file-secret");

        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            ResponseEntity<Map<String, String>> response = handler.handleMediaProcessing(exception);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals("MEDIA_PROCESSING_FAILED", response.getBody().get("code"));
            assertFalse(response.getBody().toString().contains("temporary-file-secret"));
            sentry.verify(() -> Sentry.captureException(exception));
        }
    }

    @Test
    void duplicateAiExtractionReturnsTraceIdentifiers() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        UUID requestId = UUID.randomUUID();
        UUID extractionId = UUID.randomUUID();

        ResponseEntity<Map<String, String>> response =
                handler.handleDuplicateAiExtraction(
                        new DuplicateAiExtractionException(
                                "This media was already extracted today.",
                                requestId,
                                extractionId));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("AI_EXTRACTION_DUPLICATE", response.getBody().get("code"));
        assertEquals(requestId.toString(), response.getBody().get("request_id"));
        assertEquals(extractionId.toString(), response.getBody().get("extraction_id"));
    }
}
