package com.wisekoin.backend.module.goal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wisekoin.backend.module.common.exception.BadRequestException;
import com.wisekoin.backend.module.goal.dto.GoalFundRequest;
import com.wisekoin.backend.module.transaction.Transaction;
import com.wisekoin.backend.module.transaction.TransactionRepository;
import com.wisekoin.backend.module.transaction.TransactionService;
import com.wisekoin.backend.module.transaction.dto.TransactionRequest;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserRepository;
import com.wisekoin.backend.module.wallet.Wallet;
import com.wisekoin.backend.module.wallet.WalletService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GoalServiceTest {
    private GoalRepository goalRepository;
    private TransactionService transactionService;
    private TransactionRepository transactionRepository;
    private WalletService walletService;
    private GoalService goalService;

    @BeforeEach
    void setUp() {
        goalRepository = mock(GoalRepository.class);
        transactionService = mock(TransactionService.class);
        transactionRepository = mock(TransactionRepository.class);
        walletService = mock(WalletService.class);
        goalService =
                new GoalService(
                        goalRepository,
                        mock(UserRepository.class),
                        transactionRepository,
                        transactionService,
                        walletService);
    }

    @Test
    void fundCreatesUncategorizedGoalTransfer() {
        Goal goal = goal(new BigDecimal("20.00"), new BigDecimal("100.00"));
        when(goalRepository.findById(7L)).thenReturn(Optional.of(goal));

        GoalFundRequest request = new GoalFundRequest();
        request.setWalletId(3L);
        request.setAmount(new BigDecimal("80.00"));
        request.setNote("Finish the goal");

        goalService.fund(1L, 7L, request);

        ArgumentCaptor<TransactionRequest> captor =
                ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactionService).createTransaction(eq(1L), captor.capture());
        assertNull(captor.getValue().getCategoryId());
        assertEquals(7L, captor.getValue().getGoalId());
        assertEquals(new BigDecimal("80.00"), captor.getValue().getAmount());
    }

    @Test
    void fundRejectsContributionAboveRemainingAmount() {
        Goal goal = goal(new BigDecimal("20.00"), new BigDecimal("100.00"));
        when(goalRepository.findById(7L)).thenReturn(Optional.of(goal));

        GoalFundRequest request = new GoalFundRequest();
        request.setWalletId(3L);
        request.setAmount(new BigDecimal("80.01"));

        BadRequestException exception =
                assertThrows(BadRequestException.class, () -> goalService.fund(1L, 7L, request));

        assertEquals(
                "Contribution cannot exceed the remaining goal amount", exception.getMessage());
        verify(transactionService, never())
                .createTransaction(eq(1L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteGoalRefundsEveryFundingTransactionAndRemovesHistory() {
        Goal goal = goal(new BigDecimal("50.00"), new BigDecimal("100.00"));
        Wallet firstWallet = new Wallet();
        firstWallet.setId(3L);
        Wallet secondWallet = new Wallet();
        secondWallet.setId(4L);
        Transaction first = fundingTransaction(21L, firstWallet, "20.00");
        Transaction second = fundingTransaction(22L, secondWallet, "30.00");
        when(goalRepository.findById(7L)).thenReturn(Optional.of(goal));
        when(transactionRepository.findAllByGoalId(7L))
                .thenReturn(java.util.List.of(first, second));

        goalService.deleteGoal(1L, 7L);

        verify(walletService).updateBalance(3L, new BigDecimal("20.00"));
        verify(walletService).updateBalance(4L, new BigDecimal("30.00"));
        verify(transactionRepository).deleteAll(java.util.List.of(first, second));
        verify(transactionRepository).flush();
        verify(goalRepository).delete(goal);
    }

    private Goal goal(BigDecimal currentAmount, BigDecimal targetAmount) {
        User user = new User();
        user.setId(1L);
        Goal goal = new Goal();
        goal.setId(7L);
        goal.setUser(user);
        goal.setName("Laptop");
        goal.setCurrentAmount(currentAmount);
        goal.setTargetAmount(targetAmount);
        goal.setStatus(Goal.GoalStatus.IN_PROGRESS);
        return goal;
    }

    private Transaction fundingTransaction(Long id, Wallet wallet, String amount) {
        Transaction transaction = new Transaction();
        transaction.setId(id);
        transaction.setWallet(wallet);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setType(Transaction.TransactionType.TRANSFER);
        return transaction;
    }
}
