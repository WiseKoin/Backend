package com.wisekoin.backend.module.common.event;

import com.wisekoin.backend.module.transaction.Transaction;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class TransactionUpdatedEvent {

    private Long oldWalletId;
    private BigDecimal oldAmount;
    private Transaction.TransactionType oldType;
    private Long oldDebtId;
    private Long oldGoalId;

    private Long newWalletId;
    private BigDecimal newAmount;
    private Transaction.TransactionType newType;
    private Long newDebtId;
    private Long newGoalId;
}
