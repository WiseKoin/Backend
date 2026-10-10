package com.wisekoin.backend.module.recurring.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecurringTransactionRequest {
    @NotNull
    private Long walletId;

    @NotNull
    private Long categoryId;

    @NotNull
    @Positive(message = "Amount must be greater than 0")
    private BigDecimal amount;

    private String note;

    @Min(1)
    @Max(31)
    private int dayOfMonth;

    private LocalDate startDate;
    private boolean active = true;
}
