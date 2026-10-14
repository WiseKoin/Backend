package com.wisekoin.backend.module.user.dto;

public record OtpVerifyResponse(String verificationToken, long expiresInSeconds) {}
