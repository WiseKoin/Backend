package com.wisekoin.backend.module.common.event;

import com.wisekoin.backend.module.transaction.Transaction;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TransactionDeletedEvent {
    private Long walletId;
    private Long destinationWalletId;
    private BigDecimal amount;
    private Transaction.TransactionType type;
    private Long debtId;
    private Long goalId;
}
