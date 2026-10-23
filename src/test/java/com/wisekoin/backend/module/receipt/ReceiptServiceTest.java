package com.wisekoin.backend.module.receipt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wisekoin.backend.module.ai.AiDuplicateDetector;
import com.wisekoin.backend.module.ai.adaptive.AiAdaptationService;
import com.wisekoin.backend.module.ai.adaptive.AiConfidenceScorer;
import com.wisekoin.backend.module.ai.context.AiRequestContextResolver;
import com.wisekoin.backend.module.ai.dto.AiDuplicateCandidate;
import com.wisekoin.backend.module.ai.ingestion.AiIngestionRequestService;
import com.wisekoin.backend.module.ai.media.AiMediaIngestionService;
import com.wisekoin.backend.module.ai.prompt.AiPromptService;
import com.wisekoin.backend.module.ai.provider.AiExtractionClient;
import com.wisekoin.backend.module.category.Category;
import com.wisekoin.backend.module.category.CategoryService;
import com.wisekoin.backend.module.receipt.dto.ReceiptResponse;
import com.wisekoin.backend.module.user.UserService;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ReceiptServiceTest {
    private ReceiptService receiptService;
    private AiDuplicateDetector duplicateDetector;

    @BeforeEach
    void setUp() {
        duplicateDetector = mock(AiDuplicateDetector.class);
        when(duplicateDetector.findCandidates(
                        anyLong(),
                        any(BigDecimal.class),
                        anyString(),
                        nullable(LocalDateTime.class),
                        anyString()))
                .thenReturn(List.of());
        receiptService =
                new ReceiptService(
                        mock(AiExtractionClient.class),
                        mock(AiPromptService.class),
                        mock(AiRequestContextResolver.class),
                        mock(AiAdaptationService.class),
                        mock(AiConfidenceScorer.class),
                        mock(AiMediaIngestionService.class),
                        mock(AiIngestionRequestService.class),
                        new JsonMapper(),
                        mock(CategoryService.class),
                        mock(UserService.class),
                        duplicateDetector,
                        Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Should keep Unknown valid when the user has no expense categories")
    @SuppressWarnings("unchecked")
    void buildRequest_WhenNoCategories_ShouldAllowUnknown() {
        Map<String, Object> schema = receiptService.buildResponseSchema(List.of());
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> categorySchema = (Map<String, Object>) properties.get("category_id");

        assertEquals(List.of(ReceiptService.UNKNOWN_CATEGORY_ID), categorySchema.get("enum"));
    }

    @Test
    @DisplayName("Should resolve an AI category token to the owned stable category ID")
    void parseResponse_WhenCategoryIdIsValid_ShouldReturnCategory() throws IOException {
        Category dining = category(12L, "Dining", Category.CategoryType.EXPENSE);
        String responseRaw = geminiResponse("Coffee House", "12", "45000.25");

        ReceiptResponse response =
                receiptService.parseGeminiResponse(responseRaw, List.of(dining), 1L, "VND");

        assertEquals("Coffee House", response.storeName());
        assertEquals(12L, response.categoryId());
        assertEquals("Dining", response.category());
        assertEquals(new BigDecimal("45000.25"), response.amount());
    }

    @Test
    @DisplayName("Should return Unknown without fabricating a category ID")
    void parseResponse_WhenCategoryIsUnknown_ShouldReturnNullCategoryId() throws IOException {
        String responseRaw =
                geminiResponse("Unknown merchant", ReceiptService.UNKNOWN_CATEGORY_ID, "100000");

        ReceiptResponse response =
                receiptService.parseGeminiResponse(responseRaw, List.of(), 1L, "VND");

        assertNull(response.categoryId());
        assertEquals("Unknown", response.category());
    }

    @Test
    @DisplayName("Should reject non-positive amounts")
    void parseResponse_WhenAmountIsNotPositive_ShouldThrow() {
        String responseRaw =
                geminiResponse("Unknown merchant", ReceiptService.UNKNOWN_CATEGORY_ID, "0");

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                receiptService.parseGeminiResponse(
                                        responseRaw, List.of(), 1L, "VND"));

        assertTrue(exception.getMessage().contains("positive amount"));
    }

    @Test
    @DisplayName("Should return rich receipt facts and ranked owned category candidates")
    void parseResponse_WhenRichFactsAreValid_ShouldReturnTypedFacts() throws IOException {
        Category dining = category(12L, "Dining", Category.CategoryType.EXPENSE);
        Category groceries = category(13L, "Groceries", Category.CategoryType.EXPENSE);
        String extracted =
                """
                {
                  "store_name":"Coffee House",
                  "category_id":"12",
                  "category_candidates":["12","13","999"],
                  "amount":110.00,
                  "currency":"VND",
                  "occurred_at":"2026-10-22T08:30:00",
                  "subtotal":100.00,
                  "tax":10.00,
                  "discount":0,
                  "tip":0,
                  "payment_method":"CARD",
                  "ambiguities":[]
                }
                """;

        ReceiptResponse response =
                receiptService.parseGeminiResponse(
                        geminiResponse(extracted), List.of(dining, groceries), 1L, "VND");

        assertEquals(LocalDateTime.of(2026, 10, 22, 8, 30), response.occurredAt());
        assertEquals("VND", response.currency());
        assertEquals(new BigDecimal("100.00"), response.subtotal());
        assertEquals("CARD", response.paymentMethod());
        assertEquals(
                List.of(12L, 13L),
                response.categoryCandidates().stream()
                        .map(candidate -> candidate.categoryId())
                        .toList());
        assertEquals(
                List.of(1, 2),
                response.categoryCandidates().stream().map(candidate -> candidate.rank()).toList());
    }

    @Test
    @DisplayName("Should flag semantic currency, arithmetic, date, and duplicate problems")
    void parseResponse_WhenSemanticsConflict_ShouldRequireClarification() throws IOException {
        AiDuplicateCandidate duplicate =
                new AiDuplicateCandidate(
                        99L,
                        new BigDecimal("110.00"),
                        "EXPENSE",
                        LocalDateTime.of(2026, 10, 22, 8, 30),
                        List.of("EXACT_AMOUNT"));
        when(duplicateDetector.findCandidates(
                        anyLong(),
                        any(BigDecimal.class),
                        anyString(),
                        nullable(LocalDateTime.class),
                        anyString()))
                .thenReturn(List.of(duplicate));
        String extracted =
                """
                {
                  "store_name":"Coffee House",
                  "category_id":"UNKNOWN",
                  "category_candidates":[],
                  "amount":110.00,
                  "currency":"USD",
                  "occurred_at":"2030-01-01T00:00:00",
                  "subtotal":80.00,
                  "tax":10.00,
                  "discount":0,
                  "tip":0,
                  "payment_method":"UNKNOWN",
                  "ambiguities":[]
                }
                """;

        ReceiptResponse response =
                receiptService.parseGeminiResponse(geminiResponse(extracted), List.of(), 1L, "VND");

        assertTrue(response.requiresClarification());
        assertNull(response.occurredAt());
        assertEquals(1, response.duplicateCandidates().size());
        assertTrue(
                response.ambiguities().stream()
                        .anyMatch(ambiguity -> ambiguity.code().equals("ARITHMETIC_MISMATCH")));
        assertTrue(
                response.ambiguities().stream()
                        .anyMatch(ambiguity -> ambiguity.code().equals("BASE_CURRENCY_MISMATCH")));
    }

    private Category category(Long id, String name, Category.CategoryType type) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setType(type);
        return category;
    }

    private String geminiResponse(String storeName, String categoryId, String amount) {
        String extracted =
                "{\"store_name\":\""
                        + storeName
                        + "\",\"category_id\":\""
                        + categoryId
                        + "\",\"amount\":"
                        + amount
                        + "}";
        return geminiResponse(extracted);
    }

    private String geminiResponse(String extracted) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":"
                + quoteJson(extracted)
                + "}]}}]}";
    }

    private String quoteJson(String value) {
        return "\""
                + value.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\r", "\\r")
                        .replace("\n", "\\n")
                + "\"";
    }
}
