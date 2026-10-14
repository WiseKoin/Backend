package com.wisekoin.backend.module.user.dto;

import com.wisekoin.backend.module.user.otp.OtpPurpose;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OtpVerifyRequest {
    @NotNull
    private OtpPurpose purpose;

    @Pattern(regexp = "\\d{6}", message = "Verification code must contain exactly 6 digits")
    private String code;
}
