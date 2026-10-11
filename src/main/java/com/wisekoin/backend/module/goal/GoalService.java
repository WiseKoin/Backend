package com.wisekoin.backend.module.goal;

import com.wisekoin.backend.module.common.exception.BadRequestException;
import com.wisekoin.backend.module.common.exception.ResourceNotFoundException;
import com.wisekoin.backend.module.goal.dto.GoalFundRequest;
import com.wisekoin.backend.module.goal.dto.GoalRequest;
import com.wisekoin.backend.module.goal.dto.GoalUpdateRequest;
import com.wisekoin.backend.module.transaction.Transaction;
import com.wisekoin.backend.module.transaction.TransactionRepository;
import com.wisekoin.backend.module.transaction.TransactionService;
import com.wisekoin.backend.module.transaction.dto.TransactionRequest;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoalService {
    private final GoalRepository goalRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;

    public Goal createGoal(Long userId, GoalRequest request) {
        Goal goal = new Goal();
        User userProxy = userRepository.getReferenceById(userId);

        goal.setUser(userProxy);
        goal.setName(request.getName());
        goal.setTargetAmount(request.getTargetAmount());
        goal.setCurrentAmount(BigDecimal.ZERO);
        goal.setDeadline(request.getDeadline());
        goal.setStatus(Goal.GoalStatus.IN_PROGRESS);

        return goalRepository.save(goal);
    }

    public List<Goal> getGoals(Long userId, Goal.GoalStatus status) {
        if (status != null) {
            return goalRepository.findAllByUserIdAndStatusOrderByDeadlineAsc(userId, status);
        }
        return goalRepository.findAllByUserIdOrderByDeadlineAsc(userId);
    }

    public Goal getGoalById(Long userId, Long goalId) {
        return goalRepository
                .findById(goalId)
                .filter(g -> g.getUser().getId().equals(userId))
                .orElseThrow(
                        () -> new ResourceNotFoundException("Goal not found or access denied"));
    }

    @Transactional
    public Goal fund(Long userId, Long goalId, GoalFundRequest request) {
        Goal goal = getGoalById(userId, goalId);

        if (goal.getStatus() != Goal.GoalStatus.IN_PROGRESS) {
            throw new BadRequestException("Goal is not in progress");
        }
        validateContribution(goal, request.getAmount());

        TransactionRequest transaction = new TransactionRequest();
        transaction.setWalletId(request.getWalletId());
        transaction.setAmount(request.getAmount());
        transaction.setType(Transaction.TransactionType.TRANSFER);
        transaction.setNote(
                request.getNote() != null ? request.getNote() : "Fund goal - " + goal.getName());
        transaction.setGoalId(goalId);

        transactionService.createTransaction(userId, transaction);

        return getGoalById(userId, goalId);
    }

    @Transactional
    public void applyFund(Long goalId, BigDecimal amount) {
        adjustFund(goalId, amount);
    }

    @Transactional
    public void adjustFund(Long goalId, BigDecimal amount) {
        Goal goal =
                goalRepository
                        .findById(goalId)
                        .orElseThrow(() -> new ResourceNotFoundException("Goal not found"));

        BigDecimal currentAmount = goal.getCurrentAmount().add(amount);
        if (currentAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Goal fund reversal exceeds current amount");
        }
        if (currentAmount.compareTo(goal.getTargetAmount()) > 0) {
            throw new BadRequestException("Contribution cannot exceed the remaining goal amount");
        }
        goal.setCurrentAmount(currentAmount);
        refreshStatus(goal);
        goalRepository.save(goal);
    }

    @Transactional
    public Goal updateGoal(Long userId, Long goalId, GoalRequest request) {
        Goal goal = getGoalById(userId, goalId);
        applyUpdate(goal, request.getName(), request.getTargetAmount(), request.getDeadline());
        return goalRepository.save(goal);
    }

    @Transactional
    public Goal patchGoal(Long userId, Long goalId, GoalUpdateRequest request) {
        Goal goal = getGoalById(userId, goalId);
        applyUpdate(
                goal,
                request.getName() != null ? request.getName() : goal.getName(),
                request.getTargetAmount() != null
                        ? request.getTargetAmount()
                        : goal.getTargetAmount(),
                request.isDeadlineSet() ? request.getDeadline() : goal.getDeadline());
        return goalRepository.save(goal);
    }

    @Transactional
    public void deleteGoal(Long userId, Long goalId) {
        Goal goal = getGoalById(userId, goalId);
        if (transactionRepository.existsByGoalId(goalId)) {
            throw new BadRequestException("Cannot delete a goal that is used by transactions");
        }
        goalRepository.delete(goal);
    }

    private void applyUpdate(
            Goal goal, String name, BigDecimal targetAmount, java.time.LocalDate deadline) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Goal name cannot be blank");
        }
        if (targetAmount == null || targetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Target amount must be greater than 0");
        }
        goal.setName(name);
        goal.setTargetAmount(targetAmount);
        goal.setDeadline(deadline);
        refreshStatus(goal);
    }

    private void refreshStatus(Goal goal) {
        if (goal.getStatus() == Goal.GoalStatus.CANCELLED) {
            return;
        }
        goal.setStatus(
                goal.getCurrentAmount().compareTo(goal.getTargetAmount()) >= 0
                        ? Goal.GoalStatus.ACHIEVED
                        : Goal.GoalStatus.IN_PROGRESS);
    }

    private void validateContribution(Goal goal, BigDecimal amount) {
        BigDecimal remainingAmount = goal.getTargetAmount().subtract(goal.getCurrentAmount());
        if (amount.compareTo(remainingAmount) > 0) {
            throw new BadRequestException("Contribution cannot exceed the remaining goal amount");
        }
    }
}
