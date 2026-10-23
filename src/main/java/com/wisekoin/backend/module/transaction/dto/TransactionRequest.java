package com.wisekoin.backend.module.transaction.dto;

import com.wisekoin.backend.module.transaction.Transaction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TransactionRequest {
    @NotNull
    private Long walletId;

    private Long destinationWalletId;

    private Long categoryId;

    @NotNull
    @Positive(message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotNull
    private Transaction.TransactionType type;

    private String note;

    private LocalDateTime transactionDate;

    private Long debtId;

    private Long goalId;

    private UUID aiExtractionId;

    private boolean rememberAiCategory;
}
