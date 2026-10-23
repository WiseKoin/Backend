package com.wisekoin.backend.module.ai.provider;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.wisekoin.backend.module.common.exception.AiProviderUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class GeminiAiExtractionClientTest {
    private static final String URL = "https://generativelanguage.test/models/test:generate";
    private static final String SECRET = "header-only-secret";

    private MockRestServiceServer server;
    private GeminiAiExtractionClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        AiProviderResilience resilience =
                new AiProviderResilience(
                        Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                        1,
                        Duration.ofMillis(1),
                        Duration.ofMillis(1),
                        5,
                        Duration.ofSeconds(30),
                        ignored -> {});
        client =
                new GeminiAiExtractionClient(
                        builder.build(),
                        new JsonMapper(),
                        resilience,
                        SECRET,
                        URL,
                        "test-model",
                        "",
                        "");
    }

    @Test
    void sendsCredentialInHeaderAndNeverInUrl() {
        server.expect(once(), requestTo(URL))
                .andExpect(header("x-goog-api-key", SECRET))
                .andExpect(header("x-goog-api-key", not(startsWith("?key="))))
                .andRespond(
                        withSuccess(
                                """
                                {"candidates":[{"content":{"parts":[{"text":"{\\"amount\\":\\"1.00\\"}"}]}}]}
                                """,
                                MediaType.APPLICATION_JSON));

        AiExtractionResult result = client.extract(request());

        assertEquals("{\"amount\":\"1.00\"}", result.content());
        assertEquals("test-model", result.modelVersion());
        assertFalse(URL.contains("key="));
        server.verify();
    }

    @Test
    void mapsProviderBodyToSafeUnavailableFailure() {
        server.expect(once(), requestTo(URL))
                .andRespond(
                        withServerError()
                                .body("provider-secret-payload")
                                .contentType(MediaType.TEXT_PLAIN));

        AiProviderUnavailableException failure =
                assertThrows(AiProviderUnavailableException.class, () -> client.extract(request()));

        assertEquals(
                "AI extraction is temporarily unavailable. Please try again later.",
                failure.getMessage());
        assertFalse(failure.getMessage().contains("provider-secret-payload"));
        assertFalse(failure.getCause().getMessage().contains("provider-secret-payload"));
        server.verify();
    }

    @Test
    void requestBodyLogRepresentationNeverContainsMedia() {
        RedactedAiRequestBody requestBody = new RedactedAiRequestBody();
        requestBody.put("data", "base64-sensitive-media");

        assertEquals("AI provider request [redacted]", requestBody.toString());
        assertFalse(requestBody.toString().contains("base64-sensitive-media"));
    }

    private AiExtractionRequest request() {
        return new AiExtractionRequest(
                "Extract structured data.",
                "image/jpeg",
                "base64-media",
                Map.of("type", "OBJECT"),
                0.1);
    }
}
