package com.wisekoin.backend.module.user.otp;

public interface OtpDeliveryService {
    void send(String email, OtpPurpose purpose, String code, long expirationMinutes);
}
