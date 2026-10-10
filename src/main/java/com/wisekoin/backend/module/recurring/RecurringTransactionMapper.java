package com.wisekoin.backend.module.recurring;

import com.wisekoin.backend.module.recurring.dto.RecurringTransactionResponse;
import org.springframework.stereotype.Component;

@Component
public class RecurringTransactionMapper {
    public RecurringTransactionResponse toResponse(RecurringTransaction recurring) {
        return RecurringTransactionResponse.builder()
                .id(recurring.getId())
                .amount(recurring.getAmount())
                .note(recurring.getNote())
                .dayOfMonth(recurring.getDayOfMonth())
                .nextRunDate(recurring.getNextRunDate())
                .active(recurring.isActive())
                .createdAt(recurring.getCreatedAt())
                .wallet(
                        RecurringTransactionResponse.ResourceInfo.builder()
                                .id(recurring.getWallet().getId())
                                .name(recurring.getWallet().getName())
                                .build())
                .category(
                        RecurringTransactionResponse.ResourceInfo.builder()
                                .id(recurring.getCategory().getId())
                                .name(recurring.getCategory().getName())
                                .build())
                .build();
    }
}
