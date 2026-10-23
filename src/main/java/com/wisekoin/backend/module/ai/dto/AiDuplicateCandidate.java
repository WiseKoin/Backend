package com.wisekoin.backend.module.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AiDuplicateCandidate(
        @JsonProperty("transaction_id") Long transactionId,
        BigDecimal amount,
        String type,
        @JsonProperty("transaction_date") LocalDateTime transactionDate,
        @JsonProperty("match_reasons") List<String> matchReasons) {}
