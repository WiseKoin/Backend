package com.wisekoin.backend.module.ai.provider;

public interface AiExtractionClient {
    AiExtractionResult extract(AiExtractionRequest request);
}
