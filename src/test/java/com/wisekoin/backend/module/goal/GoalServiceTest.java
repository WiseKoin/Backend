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
import com.wisekoin.backend.module.transaction.TransactionRepository;
import com.wisekoin.backend.module.transaction.TransactionService;
import com.wisekoin.backend.module.transaction.dto.TransactionRequest;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GoalServiceTest {
    private GoalRepository goalRepository;
    private TransactionService transactionService;
    private GoalService goalService;

    @BeforeEach
    void setUp() {
        goalRepository = mock(GoalRepository.class);
        transactionService = mock(TransactionService.class);
        goalService =
                new GoalService(
                        goalRepository,
                        mock(UserRepository.class),
                        mock(TransactionRepository.class),
                        transactionService);
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
}
