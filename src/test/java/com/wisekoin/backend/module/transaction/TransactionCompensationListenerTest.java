package com.wisekoin.backend.module.transaction;

import com.wisekoin.backend.module.common.event.TransactionDeletedEvent;
import com.wisekoin.backend.module.common.event.TransactionUpdatedEvent;
import com.wisekoin.backend.module.debt.DebtEventListener;
import com.wisekoin.backend.module.debt.DebtService;
import com.wisekoin.backend.module.goal.GoalEventListener;
import com.wisekoin.backend.module.goal.GoalService;
import com.wisekoin.backend.module.wallet.WalletEventListener;
import com.wisekoin.backend.module.wallet.WalletService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TransactionCompensationListenerTest {

    @Test
    void updateReversesOldWalletEffectBeforeApplyingNewEffect() {
        WalletService walletService = mock(WalletService.class);
        WalletEventListener listener = new WalletEventListener(walletService);
        TransactionUpdatedEvent event = new TransactionUpdatedEvent(
                1L, new BigDecimal("25.00"), Transaction.TransactionType.EXPENSE, null, null,
                2L, new BigDecimal("40.00"), Transaction.TransactionType.INCOME, null, null);

        listener.handleTransactionUpdated(event);

        InOrder calls = inOrder(walletService);
        calls.verify(walletService).updateBalance(1L, new BigDecimal("25.00"));
        calls.verify(walletService).updateBalance(2L, new BigDecimal("40.00"));
    }

    @Test
    void deleteReversesWalletDebtAndGoalEffects() {
        WalletService walletService = mock(WalletService.class);
        DebtService debtService = mock(DebtService.class);
        GoalService goalService = mock(GoalService.class);
        BigDecimal amount = new BigDecimal("15.00");

        new WalletEventListener(walletService).handleTransactionDeleted(
                new TransactionDeletedEvent(1L, amount, Transaction.TransactionType.EXPENSE, null, null));
        new DebtEventListener(debtService).handleTransactionDeleted(
                new TransactionDeletedEvent(1L, amount, Transaction.TransactionType.INCOME, 3L, null));
        new GoalEventListener(goalService).handleTransactionDeleted(
                new TransactionDeletedEvent(1L, amount, Transaction.TransactionType.TRANSFER, null, 4L));

        verify(walletService).updateBalance(1L, amount);
        verify(debtService).adjustRepayment(3L, amount.negate());
        verify(goalService).adjustFund(4L, amount.negate());
    }

    @Test
    void updateMovesRepaymentAndFundingBetweenLinkedEntities() {
        DebtService debtService = mock(DebtService.class);
        GoalService goalService = mock(GoalService.class);
        TransactionUpdatedEvent event = new TransactionUpdatedEvent(
                1L, new BigDecimal("10.00"), Transaction.TransactionType.INCOME, 3L, null,
                1L, new BigDecimal("12.00"), Transaction.TransactionType.TRANSFER, null, 4L);

        new DebtEventListener(debtService).handleTransactionUpdated(event);
        new GoalEventListener(goalService).handleTransactionUpdated(event);

        verify(debtService).adjustRepayment(3L, new BigDecimal("-10.00"));
        verify(goalService).adjustFund(4L, new BigDecimal("12.00"));
    }
}
