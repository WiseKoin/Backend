package com.wisekoin.backend.module.debt.dto;

import com.wisekoin.backend.module.debt.Debt;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DebtResponse {
    private Long id;
    private String counterpartName;
    private Debt.DebtType type;
    private BigDecimal totalAmount;
    private BigDecimal remainingAmount;
    private BigDecimal paidAmount;
    private LocalDate dueDate;
    private Debt.DebtStatus status;
    private String note;
}
