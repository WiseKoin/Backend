package com.wisekoin.backend.module.receipt;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ReceiptService {
    @Value("${gemini.api.vision.language.key}")
    private String apiKey;

    @Value("${gemini.api.vision.language.url}")
    private String apiUrl;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ReceiptService(ObjectMapper objectMapper) {
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
    }

    public ReceiptResponseDto extractReceipt(MultipartFile file) throws IOException {
        String mimeType = validateAndNormalizeMimeType(file);
        String base64Image = encodeImageToBase64(file);

        Map<String, Object> requestBody = buildGeminiRequestBody(mimeType, base64Image);
        String responseRaw = callGeminiApi(requestBody);

        return parseGeminiResponse(responseRaw);
    }

    String validateAndNormalizeMimeType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty.");
        }

        String rawMimeType = file.getContentType();

        if ("image/jpeg".equalsIgnoreCase(rawMimeType) || "image/jpg".equalsIgnoreCase(rawMimeType)) {
            return "image/jpeg";
        } else if ("image/png".equalsIgnoreCase(rawMimeType)) {
            return "image/png";
        } else if ("image/webp".equalsIgnoreCase(rawMimeType)) {
            return "image/webp";
        }

        throw new IllegalArgumentException("Do not support file format: " + rawMimeType + ". Only accept jpeg, png, and webp.");
    }

    private String encodeImageToBase64(MultipartFile file) throws IOException {
        return Base64.getEncoder().encodeToString(file.getBytes());
    }

    private Map<String, Object> buildGeminiRequestBody(String mimeType, String base64Image) {
        return Map.of(
            "contents", List.of(
                Map.of(
                    "parts", List.of(
                        Map.of("text", "Extract store name, category of expenditure, and total payment amount from this receipt accurately."),
                        Map.of("inline_data", Map.of(
                            "mime_type", mimeType,
                            "data", base64Image
                        ))
                    )
                )
            ),
            "generationConfig", Map.of(
                "temperature", 0.0,
                "response_mime_type", "application/json",
                "response_schema", Map.of(
                    "type", "OBJECT",
                    "properties", Map.of(
                        "store_name", Map.of("type", "STRING"),
                        "category", Map.of("type", "STRING"),
                        "amount", Map.of("type", "NUMBER")
                    ),
                    "required", List.of("store_name", "category", "amount")
                )
            )
        );
    }

    private String callGeminiApi(Map<String, Object> requestBody) {
        return restClient.post()
            .uri(apiUrl + "?key=" + apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .body(String.class);
    }

    private ReceiptResponseDto parseGeminiResponse(String responseRaw) throws IOException {
        JsonNode rootNode = objectMapper.readTree(responseRaw);
        JsonNode candidate = rootNode.path("candidates").get(0);

        if (candidate == null || candidate.path("content").isMissingNode()) {
            throw new IllegalStateException("Did not get response from AI model.");
        }

        String extractedJsonText = candidate
            .path("content")
            .path("parts").get(0)
            .path("text").asText();

        return objectMapper.readValue(extractedJsonText, ReceiptResponseDto.class);
    }
}