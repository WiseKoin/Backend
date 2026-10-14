package com.wisekoin.backend.module.user.dto;

public record OtpRequestResponse(
        long expiresInSeconds, long resendAfterSeconds, String destination) {}
