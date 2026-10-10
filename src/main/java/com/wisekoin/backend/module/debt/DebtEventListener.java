package com.wisekoin.backend.module.debt;

import com.wisekoin.backend.module.common.event.TransactionCreatedEvent;
import com.wisekoin.backend.module.common.event.TransactionDeletedEvent;
import com.wisekoin.backend.module.common.event.TransactionUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DebtEventListener {

    private final DebtService debtService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleTransactionCreated(TransactionCreatedEvent event) {
        if (event.getDebtId() != null) {
            debtService.adjustRepayment(event.getDebtId(), event.getAmount());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleTransactionUpdated(TransactionUpdatedEvent event) {
        if (event.getOldDebtId() != null) {
            debtService.adjustRepayment(event.getOldDebtId(), event.getOldAmount().negate());
        }
        if (event.getNewDebtId() != null) {
            debtService.adjustRepayment(event.getNewDebtId(), event.getNewAmount());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleTransactionDeleted(TransactionDeletedEvent event) {
        if (event.getDebtId() != null) {
            debtService.adjustRepayment(event.getDebtId(), event.getAmount().negate());
        }
    }

}
