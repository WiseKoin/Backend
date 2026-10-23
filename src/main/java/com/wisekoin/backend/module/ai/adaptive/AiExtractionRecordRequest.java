package com.wisekoin.backend.module.ai.adaptive;

import com.wisekoin.backend.module.ai.dto.AiConfidenceResponse;
import java.math.BigDecimal;

public record AiExtractionRecordRequest(
        Long userId,
        String source,
        String merchant,
        Long proposedCategoryId,
        String transactionType,
        BigDecimal amount,
        String currency,
        String promptVersion,
        String modelVersion,
        String provider,
        AiConfidenceResponse confidence,
        boolean ruleApplied,
        boolean requiresClarification,
        String locale,
        String timeZone) {}
