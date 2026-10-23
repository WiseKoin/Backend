package com.wisekoin.backend.module.receipt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wisekoin.backend.module.ai.AiDuplicateDetector;
import com.wisekoin.backend.module.ai.adaptive.AiAdaptationService;
import com.wisekoin.backend.module.ai.adaptive.AiConfidenceScorer;
import com.wisekoin.backend.module.ai.adaptive.AiExtractionRecordRequest;
import com.wisekoin.backend.module.ai.context.AiRequestContextResolver;
import com.wisekoin.backend.module.ai.ingestion.AiIngestionRequestService;
import com.wisekoin.backend.module.ai.ingestion.AiIngestionRequestService.AiIngestionAttempt;
import com.wisekoin.backend.module.ai.media.AiMediaIngestionService;
import com.wisekoin.backend.module.ai.media.AiMediaPayload;
import com.wisekoin.backend.module.ai.prompt.AiPromptService;
import com.wisekoin.backend.module.ai.provider.AiExtractionClient;
import com.wisekoin.backend.module.ai.provider.AiExtractionRequest;
import com.wisekoin.backend.module.ai.provider.AiExtractionResult;
import com.wisekoin.backend.module.category.Category;
import com.wisekoin.backend.module.category.CategoryService;
import com.wisekoin.backend.module.receipt.dto.ReceiptResponse;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.json.JsonMapper;

class ReceiptAdaptiveExtractionTest {
    @Test
    void extractionUsesVersionedContextAndReturnsTraceableApplicationConfidence() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC);
        AiExtractionClient client = mock(AiExtractionClient.class);
        CategoryService categoryService = mock(CategoryService.class);
        UserService userService = mock(UserService.class);
        AiAdaptationService adaptationService = mock(AiAdaptationService.class);
        AiDuplicateDetector duplicateDetector = mock(AiDuplicateDetector.class);
        AiMediaIngestionService mediaIngestionService = mock(AiMediaIngestionService.class);
        AiIngestionRequestService ingestionRequestService = mock(AiIngestionRequestService.class);
        UUID suggestionId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        Category dining = new Category();
        dining.setId(12L);
        dining.setName("Dining");
        dining.setType(Category.CategoryType.EXPENSE);
        User user = new User();
        user.setId(42L);
        user.setBaseCurrency("VND");

        when(categoryService.getCategoryByType(Category.CategoryType.EXPENSE, 42L))
                .thenReturn(List.of(dining));
        when(userService.getUserById(42L)).thenReturn(user);
        when(adaptationService.formatRules(42L, "EXPENSE", List.of(dining)))
                .thenReturn("coffee house -> 12 (Dining)");
        when(adaptationService.findMatchingRule(42L, "Coffee House", "EXPENSE", List.of(dining)))
                .thenReturn(Optional.of(dining));
        when(adaptationService.recordExtraction(any(AiExtractionRecordRequest.class)))
                .thenReturn(suggestionId);
        when(duplicateDetector.findCandidates(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(client.extract(any(AiExtractionRequest.class)))
                .thenReturn(
                        new AiExtractionResult(
                                """
                                {"store_name":"Coffee House","category_id":"UNKNOWN",
                                "category_candidates":[],"amount":45000,"currency":"VND",
                                "occurred_at":"2026-10-23T09:00:00","payment_method":"CARD",
                                "ambiguities":[{"field":"category_id","code":"UNCLEAR"}]}
                                """,
                                "FAKE",
                                "fake-model-v3"));
        when(mediaIngestionService.ingestReceipt(any()))
                .thenReturn(new AiMediaPayload("image/jpeg", "AQID", "fingerprint", 3));
        when(ingestionRequestService.begin(42L, "RECEIPT", null, "fingerprint"))
                .thenReturn(new AiIngestionAttempt(requestId));

        ReceiptService service =
                new ReceiptService(
                        client,
                        promptService(),
                        new AiRequestContextResolver(clock, "vi-VN"),
                        adaptationService,
                        new AiConfidenceScorer(
                                "baseline-v1",
                                false,
                                new java.math.BigDecimal("0.80"),
                                new java.math.BigDecimal("0.55")),
                        mediaIngestionService,
                        ingestionRequestService,
                        new JsonMapper(),
                        categoryService,
                        userService,
                        duplicateDetector,
                        clock);

        ReceiptResponse response =
                service.extractReceipt(
                        new MockMultipartFile(
                                "file", "receipt.jpg", "image/jpeg", new byte[] {1, 2, 3}),
                        42L,
                        "vi-VN",
                        "Asia/Ho_Chi_Minh");

        assertEquals(suggestionId, response.extractionId());
        assertEquals(requestId, response.requestId());
        assertEquals("receipt-test-v2", response.promptVersion());
        assertEquals("fake-model-v3", response.modelVersion());
        assertEquals("FAKE", response.provider());
        assertEquals("Asia/Ho_Chi_Minh", response.timeZone());
        assertEquals(12L, response.categoryId());
        assertEquals(true, response.ruleApplied());
        assertFalse(response.confidence().calibrated());
        assertNotNull(response.confidence().score());

        ArgumentCaptor<AiExtractionRequest> requestCaptor =
                ArgumentCaptor.forClass(AiExtractionRequest.class);
        verify(client).extract(requestCaptor.capture());
        verify(ingestionRequestService).complete(requestId, suggestionId);
        assertEquals(true, requestCaptor.getValue().prompt().contains("Locale: vi-VN"));
        assertEquals(
                true, requestCaptor.getValue().prompt().contains("coffee house -> 12 (Dining)"));
    }

    private AiPromptService promptService() {
        String receipt =
                """
                Current local date/time: {{CURRENT_DATETIME}}
                Locale: {{LOCALE}}
                Time zone: {{TIME_ZONE}}
                Currency: {{BASE_CURRENCY}}
                Categories: {{CATEGORIES}}
                Rules: {{MERCHANT_RULES}}
                """;
        return new AiPromptService(
                resource(receipt),
                "receipt-test-v2",
                resource("{{CURRENT_DATETIME}}"),
                "voice-test-v2");
    }

    private ByteArrayResource resource(String value) {
        return new ByteArrayResource(value.getBytes(StandardCharsets.UTF_8));
    }
}
