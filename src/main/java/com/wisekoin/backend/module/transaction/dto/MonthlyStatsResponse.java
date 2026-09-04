package com.wisekoin.backend.module.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class MonthlyStatsResponse {

    private int month;
    private int year;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;

}
