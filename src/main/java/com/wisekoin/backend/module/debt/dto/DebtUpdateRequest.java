package com.wisekoin.backend.module.debt.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wisekoin.backend.module.debt.Debt;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DebtUpdateRequest {
    private String counterpartName;
    private Debt.DebtType type;

    @Positive(message = "Total amount must be greater than 0")
    private BigDecimal totalAmount;

    private LocalDate dueDate;
    private String note;
    @JsonIgnore
    private boolean dueDateSet;
    @JsonIgnore
    private boolean noteSet;

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
        this.dueDateSet = true;
    }

    public void setNote(String note) {
        this.note = note;
        this.noteSet = true;
    }
}
