package com.wisekoin.backend.module.voice;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wisekoin.backend.module.category.Category;
import com.wisekoin.backend.module.category.CategoryService;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserService;
import com.wisekoin.backend.module.voice.dto.VoiceResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
public class VoiceService {
    static final String UNKNOWN_CATEGORY_ID = "UNKNOWN";
    static final String UNKNOWN_TYPE = "UNKNOWN";

    @Value("${gemini.api.vision.language.key}")
    private String apiKey;

    @Value("${gemini.api.vision.language.url}")
    private String apiUrl;

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final CategoryService categoryService;
    private final UserService userService;

    public VoiceResponse extractVoice(MultipartFile file, Long userId) throws IOException {
        String mimeType = validateAndNormalizeMimeType(file);
        String base64Audio = encodeAudioToBase64(file);
        String currency = getBaseCurrency(userId);

        List<Category> categories = getTransactionCategories(userId);
        Map<String, Object> requestBody =
                buildGeminiRequestBody(mimeType, base64Audio, categories, currency);
        String responseRaw = callGeminiApi(requestBody);

        return parseGeminiResponse(responseRaw, categories, currency);
    }

    List<Category> getTransactionCategories(Long userId) {
        List<Category> categories = new ArrayList<>();
        categories.addAll(categoryService.getCategoryByType(Category.CategoryType.EXPENSE, userId));
        categories.addAll(categoryService.getCategoryByType(Category.CategoryType.INCOME, userId));
        return categories;
    }

    private String getBaseCurrency(Long userId) {
        User user = userService.getUserById(userId);
        if (user.getBaseCurrency() == null || user.getBaseCurrency().isBlank()) {
            return "VND";
        }
        return user.getBaseCurrency().trim().toUpperCase(Locale.ROOT);
    }

    public String validateAndNormalizeMimeType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty.");
        }

        String rawMimeType = file.getContentType();
        String filename =
                file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";

        if ("audio/mp4".equalsIgnoreCase(rawMimeType)
                || "audio/m4a".equalsIgnoreCase(rawMimeType)
                || "audio/x-m4a".equalsIgnoreCase(rawMimeType)
                || filename.endsWith(".m4a")
                || filename.endsWith(".mp4")) {
            return "audio/mp4";
        } else if ("audio/mpeg".equalsIgnoreCase(rawMimeType)
                || "audio/mp3".equalsIgnoreCase(rawMimeType)
                || filename.endsWith(".mp3")) {
            return "audio/mp3";
        } else if ("audio/wav".equalsIgnoreCase(rawMimeType)
                || "audio/x-wav".equalsIgnoreCase(rawMimeType)
                || filename.endsWith(".wav")) {
            return "audio/wav";
        } else if ("audio/aac".equalsIgnoreCase(rawMimeType) || filename.endsWith(".aac")) {
            return "audio/aac";
        } else if ("audio/ogg".equalsIgnoreCase(rawMimeType) || filename.endsWith(".ogg")) {
            return "audio/ogg";
        }

        throw new IllegalArgumentException(
                "Do not support audio format: "
                        + rawMimeType
                        + ". Only accept m4a, mp3, wav, aac, and ogg.");
    }

    private String encodeAudioToBase64(MultipartFile file) throws IOException {
        return Base64.getEncoder().encodeToString(file.getBytes());
    }

    Map<String, Object> buildGeminiRequestBody(
            String mimeType, String base64Audio, List<Category> categories, String currency) {
        String expenseCategories = formatCategories(categories, Category.CategoryType.EXPENSE);
        String incomeCategories = formatCategories(categories, Category.CategoryType.INCOME);

        String prompt =
                "Listen to this recording of a user describing one financial transaction in Vietnamese or English. "
                        + "Extract the amount, transaction type, stable category_id, concise note, and transcript. "
                        + "Interpret the amount in the user's base currency "
                        + currency
                        + ". Expense category IDs are: "
                        + expenseCategories
                        + ". Income category IDs are: "
                        + incomeCategories
                        + ". The category_id must belong to the group matching the transaction type. "
                        + "Return category_id '"
                        + UNKNOWN_CATEGORY_ID
                        + "' when no matching category is clear. Return type '"
                        + UNKNOWN_TYPE
                        + "' when expense versus income is unclear; never guess a default type. "
                        + "Understand common Vietnamese amount expressions in the context of currency "
                        + currency
                        + ", but do not invent an amount that was not stated.";

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
                                                        "data", base64Audio))))));

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.0);
        generationConfig.put("response_mime_type", "application/json");

        List<String> categoryIds =
                new ArrayList<>(
                        categories.stream().map(category -> category.getId().toString()).toList());
        categoryIds.add(UNKNOWN_CATEGORY_ID);

        Map<String, Object> categorySchema = new HashMap<>();
        categorySchema.put("type", "STRING");
        categorySchema.put("enum", categoryIds);
        categorySchema.put(
                "description",
                "Stable ID of a category whose category type matches the transaction type, or UNKNOWN.");

        Map<String, Object> properties = new HashMap<>();
        properties.put(
                "amount",
                Map.of(
                        "type",
                        "NUMBER",
                        "description",
                        "Positive transaction amount in " + currency + " without symbols"));
        properties.put(
                "type",
                Map.of(
                        "type",
                        "STRING",
                        "enum",
                        List.of("EXPENSE", "INCOME", UNKNOWN_TYPE),
                        "description",
                        "Transaction type, or UNKNOWN when ambiguous"));
        properties.put("category_id", categorySchema);
        properties.put(
                "note",
                Map.of(
                        "type",
                        "STRING",
                        "description",
                        "Short note or description of the transaction"));
        properties.put(
                "transcript",
                Map.of(
                        "type",
                        "STRING",
                        "description",
                        "The user's spoken words transcribed in the original language"));

        Map<String, Object> responseSchema = new HashMap<>();
        responseSchema.put("type", "OBJECT");
        responseSchema.put("properties", properties);
        responseSchema.put(
                "required", List.of("amount", "type", "category_id", "note", "transcript"));

        generationConfig.put("response_schema", responseSchema);
        requestBody.put("generationConfig", generationConfig);

        return requestBody;
    }

    private String formatCategories(List<Category> categories, Category.CategoryType categoryType) {
        return categories.stream()
                .filter(category -> category.getType() == categoryType)
                .map(category -> category.getId() + " = " + category.getName())
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
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

    public VoiceResponse parseGeminiResponse(
            String responseRaw, List<Category> validCategories, String currency)
            throws IOException {
        JsonNode rootNode = jsonMapper.readTree(responseRaw);
        JsonNode candidate = rootNode.path("candidates").get(0);

        if (candidate == null || candidate.path("content").isMissingNode()) {
            throw new IllegalStateException("Did not get response from AI model.");
        }

        JsonNode part = candidate.path("content").path("parts").get(0);
        if (part == null || part.path("text").isMissingNode()) {
            throw new IllegalStateException("AI model response did not contain extracted data.");
        }

        String extractedJsonText = stripMarkdownFence(part.path("text").asString());
        VoiceExtraction extraction = jsonMapper.readValue(extractedJsonText, VoiceExtraction.class);
        validateAmount(extraction.amount());

        String type = normalizeType(extraction.type());
        Category category = findMatchingCategory(extraction.categoryId(), type, validCategories);

        return new VoiceResponse(
                extraction.amount(),
                currency,
                type,
                category != null ? category.getId() : null,
                category != null ? category.getName() : "Unknown",
                extraction.note() != null ? extraction.note() : "",
                extraction.transcript() != null ? extraction.transcript() : "");
    }

    private String stripMarkdownFence(String extractedJsonText) {
        if (extractedJsonText == null) {
            throw new IllegalStateException("AI model response did not contain extracted data.");
        }
        String result = extractedJsonText.trim();
        if (result.startsWith("```json")) {
            result = result.substring(7);
        } else if (result.startsWith("```")) {
            result = result.substring(3);
        }
        if (result.endsWith("```")) {
            result = result.substring(0, result.length() - 3);
        }
        return result.trim();
    }

    private String normalizeType(String type) {
        if ("EXPENSE".equalsIgnoreCase(type)) {
            return "EXPENSE";
        }
        if ("INCOME".equalsIgnoreCase(type)) {
            return "INCOME";
        }
        return UNKNOWN_TYPE;
    }

    private Category findMatchingCategory(
            String categoryId, String transactionType, List<Category> validCategories) {
        Category.CategoryType expectedType = expectedCategoryType(transactionType);
        if (expectedType == null
                || categoryId == null
                || UNKNOWN_CATEGORY_ID.equalsIgnoreCase(categoryId)) {
            return null;
        }
        return validCategories.stream()
                .filter(category -> category.getType() == expectedType)
                .filter(category -> category.getId().toString().equals(categoryId))
                .findFirst()
                .orElse(null);
    }

    private Category.CategoryType expectedCategoryType(String transactionType) {
        return switch (transactionType) {
            case "EXPENSE" -> Category.CategoryType.EXPENSE;
            case "INCOME" -> Category.CategoryType.INCOME;
            default -> null;
        };
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalStateException("AI model did not extract a valid positive amount.");
        }
    }

    private record VoiceExtraction(
            BigDecimal amount,
            String type,
            @JsonProperty("category_id") String categoryId,
            String note,
            String transcript) {}
}
