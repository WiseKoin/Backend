package com.wisekoin.backend.module.auth.dto;

public record PasswordRecoveryRequestResponse(long expiresInSeconds, long resendAfterSeconds) {}
