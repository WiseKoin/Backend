package com.wisekoin.backend.module.wallet;

import com.wisekoin.backend.module.common.event.TransactionCreatedEvent;
import com.wisekoin.backend.module.common.event.TransactionDeletedEvent;
import com.wisekoin.backend.module.common.event.TransactionUpdatedEvent;
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
        walletService.updateBalance(event.getWalletId(), balanceEffect(event.getAmount(), event.getType()));
        if (event.getDestinationWalletId() != null) {
            walletService.updateBalance(event.getDestinationWalletId(), event.getAmount());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleTransactionUpdated(TransactionUpdatedEvent event) {
        walletService.updateBalance(
                event.getOldWalletId(),
                balanceEffect(event.getOldAmount(), event.getOldType()).negate()
        );
        if (event.getOldDestinationWalletId() != null) {
            walletService.updateBalance(event.getOldDestinationWalletId(), event.getOldAmount().negate());
        }
        walletService.updateBalance(
                event.getNewWalletId(),
                balanceEffect(event.getNewAmount(), event.getNewType())
        );
        if (event.getNewDestinationWalletId() != null) {
            walletService.updateBalance(event.getNewDestinationWalletId(), event.getNewAmount());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleTransactionDeleted(TransactionDeletedEvent event) {
        walletService.updateBalance(
                event.getWalletId(),
                balanceEffect(event.getAmount(), event.getType()).negate()
        );
        if (event.getDestinationWalletId() != null) {
            walletService.updateBalance(event.getDestinationWalletId(), event.getAmount().negate());
        }
    }

    private BigDecimal balanceEffect(BigDecimal amount, Transaction.TransactionType type) {
        if (Transaction.TransactionType.EXPENSE.equals(type)
                || Transaction.TransactionType.TRANSFER.equals(type)) {
            return amount.negate();
        }
        return amount;
    }

}
