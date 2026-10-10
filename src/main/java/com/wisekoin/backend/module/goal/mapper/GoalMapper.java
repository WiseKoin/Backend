package com.wisekoin.backend.module.goal.mapper;

import com.wisekoin.backend.module.goal.Goal;
import com.wisekoin.backend.module.goal.dto.GoalResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class GoalMapper {
    public GoalResponse toResponse(Goal goal) {
        BigDecimal progressPercent = BigDecimal.ZERO;
        if (goal.getTargetAmount().compareTo(BigDecimal.ZERO) > 0) {
            progressPercent =
                    goal.getCurrentAmount()
                            .multiply(BigDecimal.valueOf(100))
                            .divide(goal.getTargetAmount(), 2, RoundingMode.HALF_UP);
            if (progressPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
                progressPercent = BigDecimal.valueOf(100);
            }
        }

        return GoalResponse.builder()
                .id(goal.getId())
                .name(goal.getName())
                .targetAmount(goal.getTargetAmount())
                .currentAmount(goal.getCurrentAmount())
                .remainingAmount(goal.getTargetAmount().subtract(goal.getCurrentAmount()))
                .deadline(goal.getDeadline())
                .status(goal.getStatus())
                .progressPercent(progressPercent)
                .build();
    }
}
