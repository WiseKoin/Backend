package com.wisekoin.backend.module.receipt.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wisekoin.backend.module.ai.dto.AiCategoryCandidate;
import com.wisekoin.backend.module.ai.dto.AiConfidenceResponse;
import com.wisekoin.backend.module.ai.dto.AiDuplicateCandidate;
import com.wisekoin.backend.module.ai.dto.AiFieldAmbiguity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
        @JsonProperty("duplicate_candidates") List<AiDuplicateCandidate> duplicateCandidates,
        @JsonProperty("extraction_id") UUID extractionId,
        @JsonProperty("prompt_version") String promptVersion,
        @JsonProperty("model_version") String modelVersion,
        String provider,
        String locale,
        @JsonProperty("time_zone") String timeZone,
        @JsonProperty("rule_applied") boolean ruleApplied,
        AiConfidenceResponse confidence) {
    public ReceiptResponse(
            String storeName,
            Long categoryId,
            String category,
            BigDecimal amount,
            String currency,
            LocalDateTime occurredAt,
            BigDecimal subtotal,
            BigDecimal tax,
            BigDecimal discount,
            BigDecimal tip,
            String paymentMethod,
            List<AiCategoryCandidate> categoryCandidates,
            boolean requiresClarification,
            List<AiFieldAmbiguity> ambiguities,
            List<AiDuplicateCandidate> duplicateCandidates) {
        this(
                storeName,
                categoryId,
                category,
                amount,
                currency,
                occurredAt,
                subtotal,
                tax,
                discount,
                tip,
                paymentMethod,
                categoryCandidates,
                requiresClarification,
                ambiguities,
                duplicateCandidates,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null);
    }
}
