package com.wisekoin.backend.module.recurring.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecurringTransactionResponse {
    private Long id;
    private BigDecimal amount;
    private String note;
    private int dayOfMonth;
    private LocalDate nextRunDate;
    private boolean active;
    private LocalDateTime createdAt;
    private ResourceInfo wallet;
    private ResourceInfo category;

    @Getter
    @Builder
    public static class ResourceInfo {
        private Long id;
        private String name;
    }
}
