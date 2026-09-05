package com.wisekoin.backend.module.goal;

import com.wisekoin.backend.module.common.event.TransactionCreatedEvent;
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
            goalService.applyFund(event.getGoalId(), event.getAmount());
        }
    }

}
