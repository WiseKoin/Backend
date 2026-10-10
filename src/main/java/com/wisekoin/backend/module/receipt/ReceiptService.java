package com.wisekoin.backend.module.receipt;

import com.wisekoin.backend.module.category.Category;
import com.wisekoin.backend.module.category.CategoryService;
import com.wisekoin.backend.module.receipt.dto.ReceiptResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class ReceiptService {
    @Value("${gemini.api.vision.language.key}")
    private String apiKey;

    @Value("${gemini.api.vision.language.url}")
    private String apiUrl;

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final CategoryService categoryService;

    public ReceiptResponse extractReceipt(MultipartFile file, Long userId) throws IOException {
        String mimeType = validateAndNormalizeMimeType(file);
        String base64Image = encodeImageToBase64(file);

        List<String> expenseCategories = getExpenseCategories(userId);
        Map<String, Object> requestBody =
                buildGeminiRequestBody(mimeType, base64Image, expenseCategories);
        String responseRaw = callGeminiApi(requestBody);

        return parseGeminiResponse(responseRaw, expenseCategories);
    }

    private List<String> getExpenseCategories(Long userId) {
        return categoryService.getCategoryByType(Category.CategoryType.EXPENSE, userId).stream()
                .map(Category::getName)
                .toList();
    }

    private String validateAndNormalizeMimeType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty.");
        }

        String rawMimeType = file.getContentType();

        if ("image/jpeg".equalsIgnoreCase(rawMimeType)
                || "image/jpg".equalsIgnoreCase(rawMimeType)) {
            return "image/jpeg";
        } else if ("image/png".equalsIgnoreCase(rawMimeType)) {
            return "image/png";
        } else if ("image/webp".equalsIgnoreCase(rawMimeType)) {
            return "image/webp";
        }

        throw new IllegalArgumentException(
                "Do not support file format: "
                        + rawMimeType
                        + ". Only accept jpeg, png, and webp.");
    }

    private String encodeImageToBase64(MultipartFile file) throws IOException {
        return Base64.getEncoder().encodeToString(file.getBytes());
    }

    private Map<String, Object> buildGeminiRequestBody(
            String mimeType, String base64Image, List<String> categories) {
        String categoryList = String.join(", ", categories);
        String prompt =
                "Extract store name, category of expenditure, and total payment amount from this receipt accurately. "
                        + "The category must be one of the following user-defined categories: "
                        + categoryList
                        + ". If the category cannot be matched to any of these, use 'Unknown'.";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put(
                "contents",
                List.of(
                        Map.of(
                                "parts",
                                List.of(
                                        Map.of("text", prompt),
                                        Map.of(
                                                "inline_data",
                                                Map.of(
                                                        "mime_type", mimeType,
                                                        "data", base64Image))))));

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.0);
        generationConfig.put("response_mime_type", "application/json");

        Map<String, Object> categorySchema = new HashMap<>();
        categorySchema.put("type", "STRING");
        categorySchema.put("enum", new ArrayList<>(categories));
        categorySchema.put(
                "description",
                "The expenditure category, must be one of the user-defined categories listed above or 'Unknown'.");

        Map<String, Object> properties = new HashMap<>();
        properties.put("store_name", Map.of("type", "STRING"));
        properties.put("category", categorySchema);
        properties.put("amount", Map.of("type", "NUMBER"));

        Map<String, Object> responseSchema = new HashMap<>();
        responseSchema.put("type", "OBJECT");
        responseSchema.put("properties", properties);
        responseSchema.put("required", List.of("store_name", "category", "amount"));

        generationConfig.put("response_schema", responseSchema);
        requestBody.put("generationConfig", generationConfig);

        return requestBody;
    }

    private String callGeminiApi(Map<String, Object> requestBody) {
        return restClient
                .post()
                .uri(apiUrl + "?key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);
    }

    private ReceiptResponse parseGeminiResponse(String responseRaw, List<String> validCategories)
            throws IOException {
        JsonNode rootNode = jsonMapper.readTree(responseRaw);
        JsonNode candidate = rootNode.path("candidates").get(0);

        if (candidate == null || candidate.path("content").isMissingNode()) {
            throw new IllegalStateException("Did not get response from AI model.");
        }

        String extractedJsonText =
                candidate.path("content").path("parts").get(0).path("text").asString();

        ReceiptResponse response = jsonMapper.readValue(extractedJsonText, ReceiptResponse.class);

        if (!validCategories.contains(response.category())) {
            response = new ReceiptResponse(response.storeName(), "Unknown", response.amount());
        }

        return response;
    }
}
