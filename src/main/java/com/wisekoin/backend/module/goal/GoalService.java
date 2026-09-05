package com.wisekoin.backend.module.goal;

import com.wisekoin.backend.module.common.exception.BadRequestException;
import com.wisekoin.backend.module.common.exception.ResourceNotFoundException;
import com.wisekoin.backend.module.goal.dto.GoalRequest;
import com.wisekoin.backend.module.goal.dto.GoalFundRequest;
import com.wisekoin.backend.module.transaction.Transaction;
import com.wisekoin.backend.module.transaction.TransactionRepository;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoalService {

    private final GoalRepository goalRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

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
        return goalRepository.findById(goalId)
                .filter(g -> g.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found or access denied"));
    }

    @Transactional
    public Goal fund(Long userId, Long goalId, GoalFundRequest request) {
        Goal goal = getGoalById(userId, goalId);

        if (goal.getStatus() != Goal.GoalStatus.IN_PROGRESS) {
            throw new BadRequestException("Goal is not in progress");
        }

        Transaction transaction = new Transaction();
        User userProxy = userRepository.getReferenceById(userId);
        transaction.setUser(userProxy);
        transaction.setAmount(request.getAmount());
        transaction.setType(Transaction.TransactionType.TRANSFER);
        transaction.setNote(request.getNote() != null
                ? request.getNote()
                : "Fund goal - " + goal.getName());
        transaction.setGoal(goalRepository.getReferenceById(goalId));
        transaction.setTransactionDate(java.time.LocalDateTime.now());

        transactionRepository.save(transaction);

        return getGoalById(userId, goalId);
    }

    @Transactional
    public void applyFund(Long goalId, BigDecimal amount) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found"));

        goal.setCurrentAmount(goal.getCurrentAmount().add(amount));
        if (goal.getCurrentAmount().compareTo(goal.getTargetAmount()) >= 0) {
            goal.setStatus(Goal.GoalStatus.ACHIEVED);
        }
        goalRepository.save(goal);
    }

}
