package com.wisekoin.backend.module.receipt.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wisekoin.backend.module.ai.dto.AiCategoryCandidate;
import com.wisekoin.backend.module.ai.dto.AiDuplicateCandidate;
import com.wisekoin.backend.module.ai.dto.AiFieldAmbiguity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ReceiptResponse(
        @JsonProperty("store_name") String storeName,
        @JsonProperty("category_id") Long categoryId,
        @JsonProperty("category") String category,
        @JsonProperty("amount") BigDecimal amount,
        String currency,
        @JsonProperty("occurred_at") LocalDateTime occurredAt,
        BigDecimal subtotal,
        BigDecimal tax,
        BigDecimal discount,
        BigDecimal tip,
        @JsonProperty("payment_method") String paymentMethod,
        @JsonProperty("category_candidates") List<AiCategoryCandidate> categoryCandidates,
        @JsonProperty("requires_clarification") boolean requiresClarification,
        List<AiFieldAmbiguity> ambiguities,
        @JsonProperty("duplicate_candidates") List<AiDuplicateCandidate> duplicateCandidates) {}
