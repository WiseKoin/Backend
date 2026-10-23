package com.wisekoin.backend.module.ai.provider;

import com.wisekoin.backend.module.common.exception.AiProviderUnavailableException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class GeminiAiExtractionClient implements AiExtractionClient {
    private final RestClient restClient;
    private final JsonMapper jsonMapper;
    private final AiProviderResilience resilience;
    private final String apiKey;
    private final String primaryUrl;
    private final String primaryModel;
    private final String fallbackUrl;
    private final String fallbackModel;

    public GeminiAiExtractionClient(
            RestClient restClient,
            JsonMapper jsonMapper,
            AiProviderResilience resilience,
            @Value("${gemini.api.vision.language.key}") String apiKey,
            @Value("${gemini.api.vision.language.url}") String primaryUrl,
            @Value("${gemini.api.vision.language.model:gemini-3.1-flash-lite}") String primaryModel,
            @Value("${gemini.api.vision.language.fallback-url:}") String fallbackUrl,
            @Value("${gemini.api.vision.language.fallback-model:}") String fallbackModel) {
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
        this.resilience = resilience;
        this.apiKey = apiKey;
        this.primaryUrl = primaryUrl;
        this.primaryModel = primaryModel;
        this.fallbackUrl = fallbackUrl;
        this.fallbackModel = fallbackModel;
    }

    @Override
    public AiExtractionResult extract(AiExtractionRequest request) {
        RuntimeException primaryFailure;
        try {
            return resilience.execute(
                    providerKey("primary", primaryUrl),
                    () -> call(request, primaryUrl, primaryModel));
        } catch (RestClientException
                | IllegalStateException
                | AiProviderResilienceException failure) {
            primaryFailure = failure;
        }

        if (fallbackUrl == null || fallbackUrl.isBlank()) {
            throw unavailable(primaryFailure);
        }

        String resolvedFallbackModel =
                fallbackModel == null || fallbackModel.isBlank()
                        ? "configured-fallback"
                        : fallbackModel;
        try {
            return resilience.execute(
                    providerKey("fallback", fallbackUrl),
                    () -> call(request, fallbackUrl, resolvedFallbackModel));
        } catch (RestClientException
                | IllegalStateException
                | AiProviderResilienceException fallbackFailure) {
            throw unavailable(fallbackFailure);
        }
    }

    private String providerKey(String role, String url) {
        return "gemini:" + role + ":" + url;
    }

    private AiProviderUnavailableException unavailable(RuntimeException cause) {
        return new AiProviderUnavailableException(
                "AI extraction is temporarily unavailable. Please try again later.",
                sanitizedCause(cause));
    }

    private RuntimeException sanitizedCause(RuntimeException cause) {
        if (cause instanceof RestClientResponseException responseException) {
            return new RuntimeException(
                    "AI provider returned HTTP " + responseException.getStatusCode().value() + ".");
        }
        if (cause instanceof AiCircuitOpenException) {
            return new RuntimeException("AI provider circuit is open.");
        }
        if (cause instanceof IllegalStateException) {
            return new RuntimeException("AI provider returned an invalid structured response.");
        }
        return new RuntimeException("AI provider request failed.");
    }

    private AiExtractionResult call(AiExtractionRequest request, String url, String modelVersion) {
        Map<String, Object> requestBody = new RedactedAiRequestBody();
        requestBody.put(
                "contents",
                List.of(
                        Map.of(
                                "parts",
                                List.of(
                                        Map.of("text", request.prompt()),
                                        Map.of(
                                                "inline_data",
                                                Map.of(
                                                        "mime_type",
                                                        request.mimeType(),
                                                        "data",
                                                        request.mediaBase64()))))));

        Map<String, Object> generationConfig = new RedactedAiRequestBody();
        generationConfig.put("temperature", request.temperature());
        generationConfig.put("response_mime_type", "application/json");
        generationConfig.put("response_schema", request.responseSchema());
        requestBody.put("generationConfig", generationConfig);

        String responseRaw =
                restClient
                        .post()
                        .uri(url)
                        .header("x-goog-api-key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);
        return new AiExtractionResult(extractContent(responseRaw), "GEMINI", modelVersion);
    }

    private String extractContent(String responseRaw) {
        try {
            JsonNode rootNode = jsonMapper.readTree(responseRaw);
            JsonNode candidate = rootNode.path("candidates").get(0);
            if (candidate == null || candidate.path("content").isMissingNode()) {
                throw new IllegalStateException("Did not get response from AI model.");
            }
            JsonNode part = candidate.path("content").path("parts").get(0);
            if (part == null || part.path("text").isMissingNode()) {
                throw new IllegalStateException(
                        "AI model response did not contain extracted data.");
            }
            return stripMarkdownFence(part.path("text").asString());
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("AI model returned an invalid response.", exception);
        }
    }

    private String stripMarkdownFence(String content) {
        if (content == null) {
            throw new IllegalStateException("AI model response did not contain extracted data.");
        }
        String result = content.trim();
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
}
