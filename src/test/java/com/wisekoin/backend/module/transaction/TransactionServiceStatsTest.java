package com.wisekoin.backend.module.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wisekoin.backend.module.category.CategoryRepository;
import com.wisekoin.backend.module.debt.DebtRepository;
import com.wisekoin.backend.module.goal.Goal;
import com.wisekoin.backend.module.goal.GoalRepository;
import com.wisekoin.backend.module.transaction.dto.MonthlyStatsResponse;
import com.wisekoin.backend.module.user.UserRepository;
import com.wisekoin.backend.module.wallet.Wallet;
import com.wisekoin.backend.module.wallet.WalletRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class TransactionServiceStatsTest {

    @Test
    void yearlyStatsCountsGoalFundingAsExpenseButExcludesWalletTransfers() {
        TransactionRepository transactionRepository = mock(TransactionRepository.class);
        TransactionService service =
                new TransactionService(
                        transactionRepository,
                        mock(UserRepository.class),
                        mock(WalletRepository.class),
                        mock(CategoryRepository.class),
                        mock(DebtRepository.class),
                        mock(GoalRepository.class),
                        mock(ApplicationEventPublisher.class));

        when(transactionRepository.findAllByUserIdAndTransactionDateBetween(
                        eq(9L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(
                        List.of(
                                transaction("100.00", Transaction.TransactionType.INCOME, false),
                                transaction("20.00", Transaction.TransactionType.EXPENSE, false),
                                transaction("15.00", Transaction.TransactionType.TRANSFER, true),
                                walletTransfer("40.00")));

        MonthlyStatsResponse february = service.getYearlyStats(9L, 2026).get(1);

        assertEquals(new BigDecimal("100.00"), february.getTotalIncome());
        assertEquals(new BigDecimal("35.00"), february.getTotalExpense());
    }

    private Transaction transaction(
            String amount, Transaction.TransactionType type, boolean goalFunding) {
        Transaction transaction = new Transaction();
        transaction.setAmount(new BigDecimal(amount));
        transaction.setType(type);
        transaction.setTransactionDate(LocalDateTime.of(2026, 2, 10, 12, 0));
        if (goalFunding) {
            transaction.setGoal(new Goal());
        }
        return transaction;
    }

    private Transaction walletTransfer(String amount) {
        Transaction transaction = transaction(amount, Transaction.TransactionType.TRANSFER, false);
        transaction.setDestinationWallet(new Wallet());
        return transaction;
    }
}
