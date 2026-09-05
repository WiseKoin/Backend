package com.wisekoin.backend.module.goal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class GoalRequest {

    @NotBlank
    private String name;

    @NotNull
    @Positive(message = "Target amount must be greater than 0")
    private BigDecimal targetAmount;

    private LocalDate deadline;

}
