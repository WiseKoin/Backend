package com.wisekoin.backend.module.voice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.wisekoin.backend.module.category.Category;
import com.wisekoin.backend.module.category.CategoryService;
import com.wisekoin.backend.module.voice.dto.VoiceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceService {

    @Value("${gemini.api.vision.language.key}")
    private String apiKey;

    @Value("${gemini.api.vision.language.url}")
    private String apiUrl;

    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final CategoryService categoryService;

    public VoiceResponse extractVoice(MultipartFile file, Long userId) throws IOException {
        String mimeType = validateAndNormalizeMimeType(file);
        String base64Audio = encodeAudioToBase64(file);

        List<String> userCategories = getUserCategories(userId);
        Map<String, Object> requestBody = buildGeminiRequestBody(mimeType, base64Audio, userCategories);
        String responseRaw = callGeminiApi(requestBody);

        return parseGeminiResponse(responseRaw, userCategories);
    }

    public List<String> getUserCategories(Long userId) {
        List<Category> categories = categoryService.getAllCategories(userId);
        List<String> result = new ArrayList<>();
        for (Category c : categories) {
            if (c.getName() != null && !c.getName().isBlank() && !result.contains(c.getName())) {
                result.add(c.getName());
            }
        }

        if (result.isEmpty()) {
            result.addAll(List.of("Dining", "Transport", "Shopping", "Education", "Entertainment", "Salary", "Bonus", "General"));
        }

        return result;
    }

    public String validateAndNormalizeMimeType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty.");
        }

        String rawMimeType = file.getContentType();
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";

        if ("audio/mp4".equalsIgnoreCase(rawMimeType) || "audio/m4a".equalsIgnoreCase(rawMimeType)
                || "audio/x-m4a".equalsIgnoreCase(rawMimeType) || filename.endsWith(".m4a") || filename.endsWith(".mp4")) {
            return "audio/mp4";
        } else if ("audio/mpeg".equalsIgnoreCase(rawMimeType) || "audio/mp3".equalsIgnoreCase(rawMimeType) || filename.endsWith(".mp3")) {
            return "audio/mp3";
        } else if ("audio/wav".equalsIgnoreCase(rawMimeType) || "audio/x-wav".equalsIgnoreCase(rawMimeType) || filename.endsWith(".wav")) {
            return "audio/wav";
        } else if ("audio/aac".equalsIgnoreCase(rawMimeType) || filename.endsWith(".aac")) {
            return "audio/aac";
        } else if ("audio/ogg".equalsIgnoreCase(rawMimeType) || filename.endsWith(".ogg")) {
            return "audio/ogg";
        }

        throw new IllegalArgumentException("Do not support audio format: " + rawMimeType + ". Only accept m4a, mp3, wav, aac, and ogg.");
    }

    private String encodeAudioToBase64(MultipartFile file) throws IOException {
        return Base64.getEncoder().encodeToString(file.getBytes());
    }

    private Map<String, Object> buildGeminiRequestBody(String mimeType, String base64Audio, List<String> categories) {
        List<String> categoriesWithUnknown = new ArrayList<>(categories);
        if (!categoriesWithUnknown.contains("Unknown")) {
            categoriesWithUnknown.add("Unknown");
        }
        String categoryList = String.join(", ", categoriesWithUnknown);

        String prompt = "Listen carefully to this audio recording of a user describing a financial transaction (expense or income) in Vietnamese or English. "
                + "Extract the transaction amount, transaction type (EXPENSE or INCOME), category of expenditure or income, a concise note/description, and full transcript. "
                + "The category must be chosen from the following user categories: " + categoryList
                + ". If the transaction does not match any listed category, choose 'Unknown'. "
                + "Accurately understand spoken Vietnamese numbers and terms (e.g., '50k', '50 ngàn', '50 nghìn' -> 50000; '1 củ', '1 triệu' -> 1000000; '2 lít', '200k' -> 200000; 'nửa triệu' -> 500000). "
                + "Default type is 'EXPENSE' unless the user clearly states income, earnings, or salary.";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(
                Map.of(
                        "parts", List.of(
                                Map.of("text", prompt),
                                Map.of("inline_data", Map.of(
                                        "mime_type", mimeType,
                                        "data", base64Audio
                                ))
                        )
                )
        ));

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.0);
        generationConfig.put("response_mime_type", "application/json");

        Map<String, Object> categorySchema = new HashMap<>();
        categorySchema.put("type", "STRING");
        categorySchema.put("enum", categoriesWithUnknown);
        categorySchema.put("description", "The expenditure or income category from user-defined list or 'Unknown'.");

        Map<String, Object> properties = new HashMap<>();
        properties.put("amount", Map.of("type", "NUMBER", "description", "The numeric transaction amount in VND without symbols"));
        properties.put("type", Map.of("type", "STRING", "enum", List.of("EXPENSE", "INCOME"), "description", "Transaction type: EXPENSE or INCOME"));
        properties.put("category", categorySchema);
        properties.put("note", Map.of("type", "STRING", "description", "Short note or description of the transaction"));
        properties.put("transcript", Map.of("type", "STRING", "description", "The user's spoken words transcribed in the original language"));

        Map<String, Object> responseSchema = new HashMap<>();
        responseSchema.put("type", "OBJECT");
        responseSchema.put("properties", properties);
        responseSchema.put("required", List.of("amount", "type", "category", "note"));

        generationConfig.put("response_schema", responseSchema);
        requestBody.put("generationConfig", generationConfig);

        return requestBody;
    }

    private String callGeminiApi(Map<String, Object> requestBody) {
        return restClient.post()
                .uri(apiUrl + "?key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);
    }

    public VoiceResponse parseGeminiResponse(String responseRaw, List<String> validCategories) throws IOException {
        JsonNode rootNode = jsonMapper.readTree(responseRaw);
        JsonNode candidate = rootNode.path("candidates").get(0);

        if (candidate == null || candidate.path("content").isMissingNode()) {
            throw new IllegalStateException("Did not get response from AI model.");
        }

        String extractedJsonText = candidate
                .path("content")
                .path("parts").get(0)
                .path("text").asString();

        if (extractedJsonText != null) {
            extractedJsonText = extractedJsonText.trim();
            if (extractedJsonText.startsWith("```json")) {
                extractedJsonText = extractedJsonText.substring(7);
            } else if (extractedJsonText.startsWith("```")) {
                extractedJsonText = extractedJsonText.substring(3);
            }
            if (extractedJsonText.endsWith("```")) {
                extractedJsonText = extractedJsonText.substring(0, extractedJsonText.length() - 3);
            }
            extractedJsonText = extractedJsonText.trim();
        }

        VoiceResponse response = jsonMapper.readValue(extractedJsonText, VoiceResponse.class);

        String category = response.category();
        if (category == null || !validCategories.contains(category)) {
            category = "Unknown";
        }

        String type = response.type();
        if (type == null || (!"EXPENSE".equalsIgnoreCase(type) && !"INCOME".equalsIgnoreCase(type))) {
            type = "EXPENSE";
        } else {
            type = type.toUpperCase();
        }

        return new VoiceResponse(
                response.amount(),
                type,
                category,
                response.note() != null ? response.note() : "",
                response.transcript() != null ? response.transcript() : ""
        );
    }

}
