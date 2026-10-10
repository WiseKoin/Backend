package com.wisekoin.backend.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BaseCurrencyUpdateRequest {
    @NotBlank
    @Pattern(regexp = "[A-Za-z]{3}", message = "must be a three-letter ISO 4217 code")
    private String baseCurrency;
}
