package com.wisekoin.backend.module.goal.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GoalFundRequest {
    @NotNull
    @Positive(message = "Fund amount must be greater than 0")
    private BigDecimal amount;

    @NotNull
    private Long walletId;

    @NotNull
    private Long categoryId;

    private String note;
}
