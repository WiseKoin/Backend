package com.wisekoin.backend.module.ai.suggestion.dto;

import com.wisekoin.backend.module.transaction.Transaction;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiSuggestionConfirmationRequest {
    @Positive
    private Long walletId;

    @Positive
    private Long destinationWalletId;

    @Positive
    private Long categoryId;

    @Positive(message = "Amount must be greater than 0")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal amount;

    private Transaction.TransactionType type;

    @Size(max = 1000)
    private String note;

    private LocalDateTime transactionDate;

    private boolean rememberAiCategory;
}
