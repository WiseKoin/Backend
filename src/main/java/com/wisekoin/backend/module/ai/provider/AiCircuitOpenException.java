package com.wisekoin.backend.module.ai.provider;

final class AiCircuitOpenException extends AiProviderResilienceException {
    AiCircuitOpenException() {
        super("AI provider circuit is open.");
    }
}
