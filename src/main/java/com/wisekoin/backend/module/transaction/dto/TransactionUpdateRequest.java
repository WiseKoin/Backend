package com.wisekoin.backend.module.transaction.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wisekoin.backend.module.transaction.Transaction;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class TransactionUpdateRequest {
    private Long walletId;
    private Long destinationWalletId;
    private Long categoryId;

    @Positive(message = "Amount must be greater than 0")
    private BigDecimal amount;

    private Transaction.TransactionType type;
    private String note;
    private LocalDateTime transactionDate;
    private Long debtId;
    private Long goalId;

    @JsonIgnore
    private boolean debtIdSet;
    @JsonIgnore
    private boolean goalIdSet;
    @JsonIgnore
    private boolean noteSet;
    @JsonIgnore
    private boolean destinationWalletIdSet;

    public void setNote(String note) {
        this.note = note;
        this.noteSet = true;
    }

    public void setDebtId(Long debtId) {
        this.debtId = debtId;
        this.debtIdSet = true;
    }

    public void setGoalId(Long goalId) {
        this.goalId = goalId;
        this.goalIdSet = true;
    }

    public void setDestinationWalletId(Long destinationWalletId) {
        this.destinationWalletId = destinationWalletId;
        this.destinationWalletIdSet = true;
    }
}
