package com.wisekoin.backend.module.common.exception;

public class PasswordVerificationException extends BadRequestException {
    public PasswordVerificationException(String message) {
        super(message);
    }
}
