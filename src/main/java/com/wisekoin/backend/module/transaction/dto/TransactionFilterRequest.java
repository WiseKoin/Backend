package com.wisekoin.backend.module.transaction.dto;

import com.wisekoin.backend.module.transaction.Transaction;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
public class TransactionFilterRequest {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    private LocalDate endDate;

    private Long categoryId;

    private Long walletId;

    private Transaction.TransactionType type;

    private Long debtId;

    private Long goalId;

}
