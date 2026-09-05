package com.wisekoin.backend.module.debt.mapper;

import com.wisekoin.backend.module.debt.Debt;
import com.wisekoin.backend.module.debt.dto.DebtResponse;
import org.springframework.stereotype.Component;

@Component
public class DebtMapper {

    public DebtResponse toResponse(Debt debt) {
        return DebtResponse.builder()
                .id(debt.getId())
                .counterpartName(debt.getCounterpartName())
                .type(debt.getType())
                .totalAmount(debt.getTotalAmount())
                .remainingAmount(debt.getRemainingAmount())
                .paidAmount(debt.getTotalAmount().subtract(debt.getRemainingAmount()))
                .dueDate(debt.getDueDate())
                .status(debt.getStatus())
                .note(debt.getNote())
                .build();
    }

}
