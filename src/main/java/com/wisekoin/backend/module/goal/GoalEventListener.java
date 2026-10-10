package com.wisekoin.backend.module.goal;

import com.wisekoin.backend.module.common.event.TransactionCreatedEvent;
import com.wisekoin.backend.module.common.event.TransactionDeletedEvent;
import com.wisekoin.backend.module.common.event.TransactionUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class GoalEventListener {

    private final GoalService goalService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleTransactionCreated(TransactionCreatedEvent event) {
        if (event.getGoalId() != null) {
            goalService.adjustFund(event.getGoalId(), event.getAmount());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleTransactionUpdated(TransactionUpdatedEvent event) {
        if (event.getOldGoalId() != null) {
            goalService.adjustFund(event.getOldGoalId(), event.getOldAmount().negate());
        }
        if (event.getNewGoalId() != null) {
            goalService.adjustFund(event.getNewGoalId(), event.getNewAmount());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleTransactionDeleted(TransactionDeletedEvent event) {
        if (event.getGoalId() != null) {
            goalService.adjustFund(event.getGoalId(), event.getAmount().negate());
        }
    }

}
