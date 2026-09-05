package com.wisekoin.backend.module.common.event;

import com.wisekoin.backend.module.transaction.Transaction;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class TransactionCreatedEvent {

    private Long walletId;
    private BigDecimal amount;
    private Transaction.TransactionType type;
    private Long debtId;
    private Long goalId;

}
