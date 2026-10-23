package com.wisekoin.backend.module.common.exception;

import java.util.UUID;
import lombok.Getter;

@Getter
public class DuplicateAiExtractionException extends RuntimeException {
    private final UUID requestId;
    private final UUID extractionId;

    public DuplicateAiExtractionException(String message, UUID requestId, UUID extractionId) {
        super(message);
        this.requestId = requestId;
        this.extractionId = extractionId;
    }
}
