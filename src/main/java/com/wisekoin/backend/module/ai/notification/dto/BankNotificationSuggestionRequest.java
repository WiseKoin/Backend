package com.wisekoin.backend.module.ai.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wisekoin.backend.module.transaction.Transaction;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BankNotificationSuggestionRequest(
        @NotBlank @Pattern(regexp = "VCB|MB|TECHCOMBANK|MOMO") String source,
        @JsonProperty("source_reference_hash") @NotBlank @Pattern(regexp = "(?i)[0-9a-f]{64}")
                String sourceReferenceHash,
        @JsonProperty("parser_version") @NotBlank @Size(max = 50) String parserVersion,
        @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount,
        @NotBlank @Pattern(regexp = "(?i)[A-Z]{3}") String currency,
        @NotNull Transaction.TransactionType type,
        @JsonProperty("occurred_at") @NotNull LocalDateTime occurredAt,
        @NotBlank @Size(max = 35) String locale,
        @JsonProperty("time_zone") @NotBlank @Size(max = 100) String timeZone) {}
