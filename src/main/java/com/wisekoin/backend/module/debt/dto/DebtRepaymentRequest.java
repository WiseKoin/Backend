package com.wisekoin.backend.module.debt.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DebtRepaymentRequest {
    @NotNull
    @Positive(message = "Repayment amount must be greater than 0")
    private BigDecimal amount;

    @NotNull
    private Long walletId;

    @NotNull
    private Long categoryId;

    private String note;
}
