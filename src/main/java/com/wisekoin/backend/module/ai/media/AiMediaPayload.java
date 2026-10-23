package com.wisekoin.backend.module.ai.media;

public record AiMediaPayload(
        String mimeType, String base64Content, String fingerprint, long sizeBytes) {}
