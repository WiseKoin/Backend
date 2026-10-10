package com.wisekoin.backend.module.recurring.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecurringTransactionUpdateRequest {
    private Long walletId;
    private Long categoryId;

    @Positive(message = "Amount must be greater than 0")
    private BigDecimal amount;

    private String note;

    @Min(1)
    @Max(31)
    private Integer dayOfMonth;

    private LocalDate startDate;
    private Boolean active;

    @JsonIgnore
    private boolean noteSet;

    public void setNote(String note) {
        this.note = note;
        this.noteSet = true;
    }
}
