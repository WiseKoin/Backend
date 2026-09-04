package com.wisekoin.backend.module.transaction.dto;

import com.wisekoin.backend.module.transaction.Transaction;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class TransactionResponse {

    private Long id;
    private BigDecimal amount;
    private Transaction.TransactionType type;
    private String note;
    private LocalDateTime transactionDate;

    private WalletInfo wallet;
    private CategoryInfo category;

    @Getter
    @Builder
    public static class WalletInfo {
        private Long id;
        private String name;
    }

    @Getter
    @Builder
    public static class CategoryInfo {
        private Long id;
        private String name;
        private String icon;
    }

}
