package com.wisekoin.backend.module.ai.suggestion.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wisekoin.backend.module.transaction.Transaction;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class AiSuggestionClarificationRequest {
    @Positive(message = "Amount must be greater than 0")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal amount;

    @Pattern(regexp = "(?i)[A-Z]{3}", message = "Currency must be an ISO 4217 code")
    private String currency;

    private Transaction.TransactionType type;

    @Positive
    private Long categoryId;

    private LocalDateTime occurredAt;

    @Positive
    private Long sourceWalletId;

    @Positive
    private Long destinationWalletId;

    @Size(max = 200)
    private String merchant;

    private Boolean acknowledgePossibleDuplicate;

    @JsonIgnore
    private boolean categoryIdSet;

    @JsonIgnore
    private boolean occurredAtSet;

    @JsonIgnore
    private boolean sourceWalletIdSet;

    @JsonIgnore
    private boolean destinationWalletIdSet;

    @JsonIgnore
    private boolean merchantSet;

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setType(Transaction.TransactionType type) {
        this.type = type;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
        this.categoryIdSet = true;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
        this.occurredAtSet = true;
    }

    public void setSourceWalletId(Long sourceWalletId) {
        this.sourceWalletId = sourceWalletId;
        this.sourceWalletIdSet = true;
    }

    public void setDestinationWalletId(Long destinationWalletId) {
        this.destinationWalletId = destinationWalletId;
        this.destinationWalletIdSet = true;
    }

    public void setMerchant(String merchant) {
        this.merchant = merchant;
        this.merchantSet = true;
    }

    public void setAcknowledgePossibleDuplicate(Boolean acknowledgePossibleDuplicate) {
        this.acknowledgePossibleDuplicate = acknowledgePossibleDuplicate;
    }

    @JsonIgnore
    public boolean hasChanges() {
        return amount != null
                || currency != null
                || type != null
                || categoryIdSet
                || occurredAtSet
                || sourceWalletIdSet
                || destinationWalletIdSet
                || merchantSet
                || Boolean.TRUE.equals(acknowledgePossibleDuplicate);
    }
}
