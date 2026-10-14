package com.wisekoin.backend.module.common.exception;

public class InvalidOtpException extends BadRequestException {
    public InvalidOtpException(String message) {
        super(message);
    }
}
