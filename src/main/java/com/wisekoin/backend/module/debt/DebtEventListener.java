package com.wisekoin.backend.module.debt;

import com.wisekoin.backend.module.common.event.TransactionCreatedEvent;
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
            debtService.applyRepayment(event.getDebtId(), event.getAmount());
        }
    }

}
