package com.wisekoin.backend.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeleteAccountRequest {
    @NotBlank
    private String currentPassword;

    @NotBlank
    private String verificationToken;
}
