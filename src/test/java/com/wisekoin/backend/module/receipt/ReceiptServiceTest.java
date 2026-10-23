package com.wisekoin.backend.module.receipt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.wisekoin.backend.module.category.Category;
import com.wisekoin.backend.module.category.CategoryService;
import com.wisekoin.backend.module.receipt.dto.ReceiptResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class ReceiptServiceTest {
    private ReceiptService receiptService;

    @BeforeEach
    void setUp() {
        receiptService =
                new ReceiptService(
                        mock(RestClient.class), new JsonMapper(), mock(CategoryService.class));
    }

    @Test
    @DisplayName("Should normalize image/jpg to image/jpeg")
    void validateMimeType_WhenJpg_ShouldReturnJpeg() {
        MockMultipartFile file =
                new MockMultipartFile("file", "bill.jpg", "image/jpg", "fake-bytes".getBytes());

        assertEquals("image/jpeg", receiptService.validateAndNormalizeMimeType(file));
    }

    @Test
    @DisplayName("Should accept PNG and WebP images")
    void validateMimeType_WhenPngOrWebp_ShouldReturnExactType() {
        MockMultipartFile pngFile =
                new MockMultipartFile("file", "bill.png", "image/png", "fake-bytes".getBytes());
        MockMultipartFile webpFile =
                new MockMultipartFile("file", "bill.webp", "image/webp", "fake-bytes".getBytes());

        assertEquals("image/png", receiptService.validateAndNormalizeMimeType(pngFile));
        assertEquals("image/webp", receiptService.validateAndNormalizeMimeType(webpFile));
    }

    @Test
    @DisplayName("Should reject empty, null, and unsupported image files")
    void validateMimeType_WhenInvalid_ShouldThrowException() {
        MockMultipartFile emptyFile =
                new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);
        MockMultipartFile pdfFile =
                new MockMultipartFile(
                        "file", "document.pdf", "application/pdf", "fake-bytes".getBytes());

        assertThrows(
                IllegalArgumentException.class,
                () -> receiptService.validateAndNormalizeMimeType(emptyFile));
        assertThrows(
                IllegalArgumentException.class,
                () -> receiptService.validateAndNormalizeMimeType(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> receiptService.validateAndNormalizeMimeType(pdfFile));
    }

    @Test
    @DisplayName("Should keep Unknown valid when the user has no expense categories")
    @SuppressWarnings("unchecked")
    void buildRequest_WhenNoCategories_ShouldAllowUnknown() {
        Map<String, Object> request =
                receiptService.buildGeminiRequestBody("image/jpeg", "image-data", List.of());
        Map<String, Object> generationConfig =
                (Map<String, Object>) request.get("generationConfig");
        Map<String, Object> schema = (Map<String, Object>) generationConfig.get("response_schema");
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> categorySchema = (Map<String, Object>) properties.get("category_id");

        assertEquals(List.of(ReceiptService.UNKNOWN_CATEGORY_ID), categorySchema.get("enum"));
    }

    @Test
    @DisplayName("Should resolve an AI category token to the owned stable category ID")
    void parseResponse_WhenCategoryIdIsValid_ShouldReturnCategory() throws IOException {
        Category dining = category(12L, "Dining", Category.CategoryType.EXPENSE);
        String responseRaw = geminiResponse("Coffee House", "12", "45000.25");

        ReceiptResponse response = receiptService.parseGeminiResponse(responseRaw, List.of(dining));

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

        ReceiptResponse response = receiptService.parseGeminiResponse(responseRaw, List.of());

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
                        () -> receiptService.parseGeminiResponse(responseRaw, List.of()));

        assertTrue(exception.getMessage().contains("positive amount"));
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
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":"
                + quoteJson(extracted)
                + "}]}}]}";
    }

    private String quoteJson(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
