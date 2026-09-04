package com.wisekoin.backend.module.wallet;

import com.wisekoin.backend.module.common.event.TransactionCreatedEvent;
import com.wisekoin.backend.module.transaction.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class WalletEventListener {

    private final WalletService walletService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleTransactionCreated(TransactionCreatedEvent event) {
        BigDecimal amountToUpdate = event.getAmount();

        if (Transaction.TransactionType.EXPENSE.equals(event.getType())) {
            amountToUpdate = amountToUpdate.negate();
        }

        walletService.updateBalance(event.getWalletId(), amountToUpdate);
    }

}
