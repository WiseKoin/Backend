package com.wisekoin.backend.module.ai.provider;

import java.util.Map;

public record AiExtractionRequest(
        String prompt,
        String mimeType,
        String mediaBase64,
        Map<String, Object> responseSchema,
        double temperature) {}
