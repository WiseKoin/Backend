package com.wisekoin.backend.module.transaction.dto;

import com.wisekoin.backend.module.transaction.Transaction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class TransactionRequest {

    @NotNull
    private Long walletId;

    private Long destinationWalletId;

    @NotNull
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

}
