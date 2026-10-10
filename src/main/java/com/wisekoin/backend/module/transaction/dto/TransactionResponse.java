package com.wisekoin.backend.module.transaction.dto;

import com.wisekoin.backend.module.transaction.Transaction;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TransactionResponse {
    private Long id;
    private BigDecimal amount;
    private Transaction.TransactionType type;
    private String note;
    private LocalDateTime transactionDate;

    private WalletInfo wallet;
    private WalletInfo destinationWallet;
    private CategoryInfo category;
    private DebtInfo debt;
    private GoalInfo goal;

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

    @Getter
    @Builder
    public static class DebtInfo {
        private Long id;
        private String counterpartName;
    }

    @Getter
    @Builder
    public static class GoalInfo {
        private Long id;
        private String name;
    }
}
