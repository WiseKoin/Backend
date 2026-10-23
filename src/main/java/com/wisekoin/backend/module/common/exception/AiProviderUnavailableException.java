package com.wisekoin.backend.module.common.exception;

public class AiProviderUnavailableException extends ServiceUnavailableException {
    public AiProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
