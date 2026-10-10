package com.wisekoin.backend.module.goal.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoalUpdateRequest {
    private String name;

    @Positive(message = "Target amount must be greater than 0")
    private BigDecimal targetAmount;

    private LocalDate deadline;
    @JsonIgnore
    private boolean deadlineSet;

    public void setDeadline(LocalDate deadline) {
        this.deadline = deadline;
        this.deadlineSet = true;
    }
}
