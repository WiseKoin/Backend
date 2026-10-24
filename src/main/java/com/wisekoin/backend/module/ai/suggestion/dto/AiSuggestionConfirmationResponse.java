package com.wisekoin.backend.module.ai.suggestion.dto;

import com.wisekoin.backend.module.transaction.dto.TransactionResponse;

public record AiSuggestionConfirmationResponse(
        AiSuggestionResponse suggestion, TransactionResponse transaction) {}
