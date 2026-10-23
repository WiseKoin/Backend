package com.wisekoin.backend.module.ai.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class AiPromptServiceTest {
    @Test
    void rendersVersionedPromptValues() {
        AiPromptService service = service("Locale {{LOCALE}}; rules {{MERCHANT_RULES}}");

        VersionedPrompt prompt =
                service.receipt(Map.of("LOCALE", "vi-VN", "MERCHANT_RULES", "coffee -> 7"));

        assertEquals("receipt-test-v2", prompt.version());
        assertTrue(prompt.content().contains("Locale vi-VN"));
        assertTrue(prompt.content().contains("coffee -> 7"));
    }

    @Test
    void refusesPromptWithUnresolvedTemplateValue() {
        AiPromptService service = service("Locale {{LOCALE}}; rules {{MERCHANT_RULES}}");

        assertThrows(IllegalStateException.class, () -> service.receipt(Map.of("LOCALE", "vi-VN")));
    }

    private AiPromptService service(String receiptTemplate) {
        return new AiPromptService(
                resource(receiptTemplate),
                "receipt-test-v2",
                resource("Voice {{LOCALE}}"),
                "voice-test-v2");
    }

    private ByteArrayResource resource(String value) {
        return new ByteArrayResource(value.getBytes(StandardCharsets.UTF_8));
    }
}
