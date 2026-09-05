package com.wisekoin.backend.module.goal.dto;

import com.wisekoin.backend.module.goal.Goal;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class GoalResponse {

    private Long id;
    private String name;
    private BigDecimal targetAmount;
    private BigDecimal currentAmount;
    private BigDecimal remainingAmount;
    private LocalDate deadline;
    private Goal.GoalStatus status;
    private BigDecimal progressPercent;

}
