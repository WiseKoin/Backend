package com.wisekoin.backend.module.ai.provider;

class AiProviderResilienceException extends RuntimeException {
    AiProviderResilienceException(String message) {
        super(message);
    }

    AiProviderResilienceException(String message, Throwable cause) {
        super(message, cause);
    }
}
