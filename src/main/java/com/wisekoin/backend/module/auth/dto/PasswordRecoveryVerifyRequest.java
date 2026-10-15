package com.wisekoin.backend.module.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordRecoveryVerifyRequest {
    @Email
    @NotBlank private String email;

    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "Verification code must contain 6 digits")
    private String code;
}
