package com.wisekoin.backend.module.ai.suggestion.dto;

import jakarta.validation.constraints.NotNull;

public record AiSuggestionRejectionRequest(@NotNull Reason reason) {
    public enum Reason {
        DUPLICATE,
        INCORRECT,
        NOT_RELEVANT,
        PRIVACY,
        OTHER
    }
}
