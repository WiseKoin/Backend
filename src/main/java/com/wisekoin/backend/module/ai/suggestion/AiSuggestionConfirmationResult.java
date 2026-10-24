package com.wisekoin.backend.module.ai.suggestion;

import com.wisekoin.backend.module.ai.suggestion.dto.AiSuggestionResponse;
import com.wisekoin.backend.module.transaction.Transaction;

public record AiSuggestionConfirmationResult(
        AiSuggestionResponse suggestion, Transaction transaction) {}
