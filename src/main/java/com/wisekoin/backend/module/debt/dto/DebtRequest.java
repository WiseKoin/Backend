package com.wisekoin.backend.module.debt.dto;

import com.wisekoin.backend.module.debt.Debt;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class DebtRequest {

    @NotBlank
    private String counterpartName;

    @NotNull
    private Debt.DebtType type;

    @NotNull
    @Positive(message = "Total amount must be greater than 0")
    private BigDecimal totalAmount;

    private LocalDate dueDate;

    private String note;

}
