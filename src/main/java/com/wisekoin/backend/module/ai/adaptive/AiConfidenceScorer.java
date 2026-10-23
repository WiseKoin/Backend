package com.wisekoin.backend.module.ai.adaptive;

import com.wisekoin.backend.module.ai.dto.AiConfidenceResponse;
import com.wisekoin.backend.module.ai.dto.AiFieldAmbiguity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiConfidenceScorer {
    private final String profile;
    private final boolean calibrated;
    private final BigDecimal highThreshold;
    private final BigDecimal mediumThreshold;

    public AiConfidenceScorer(
            @Value("${app.ai.confidence.profile:baseline-v1}") String profile,
            @Value("${app.ai.confidence.calibrated:false}") boolean calibrated,
            @Value("${app.ai.confidence.high-threshold:0.80}") BigDecimal highThreshold,
            @Value("${app.ai.confidence.medium-threshold:0.55}") BigDecimal mediumThreshold) {
        if (mediumThreshold.signum() < 0
                || highThreshold.compareTo(BigDecimal.ONE) > 0
                || mediumThreshold.compareTo(highThreshold) > 0) {
            throw new IllegalArgumentException("Invalid AI confidence thresholds.");
        }
        this.profile = profile;
        this.calibrated = calibrated;
        this.highThreshold = highThreshold;
        this.mediumThreshold = mediumThreshold;
    }

    public AiConfidenceResponse score(
            List<AiFieldAmbiguity> ambiguities, boolean hasDuplicates, boolean ruleApplied) {
        List<String> reasons = new ArrayList<>();
        BigDecimal penalty = BigDecimal.ZERO;
        if (ambiguities != null) {
            for (AiFieldAmbiguity ambiguity : ambiguities.stream().distinct().toList()) {
                String code = ambiguity.code().toUpperCase(Locale.ROOT);
                BigDecimal weight = ambiguityPenalty(code);
                penalty = penalty.add(weight);
                reasons.add("AMBIGUITY_" + code);
            }
        }
        if (hasDuplicates) {
            penalty = penalty.add(new BigDecimal("0.20"));
            reasons.add("POSSIBLE_DUPLICATE");
        }
        if (ruleApplied) {
            penalty = penalty.subtract(new BigDecimal("0.05"));
            reasons.add("CONFIRMED_MERCHANT_RULE");
        }

        BigDecimal score =
                BigDecimal.ONE
                        .subtract(penalty)
                        .max(BigDecimal.ZERO)
                        .min(BigDecimal.ONE)
                        .setScale(4, RoundingMode.HALF_UP);
        String level =
                score.compareTo(highThreshold) >= 0
                        ? "HIGH"
                        : score.compareTo(mediumThreshold) >= 0 ? "MEDIUM" : "LOW";
        if (reasons.isEmpty()) {
            reasons.add("NO_VALIDATION_WARNINGS");
        }
        return new AiConfidenceResponse(score, level, List.copyOf(reasons), profile, calibrated);
    }

    private BigDecimal ambiguityPenalty(String code) {
        return switch (code) {
            case "MISSING", "INVALID", "UNRESOLVED", "OUT_OF_RANGE" -> new BigDecimal("0.25");
            case "CONFLICTING", "ARITHMETIC_MISMATCH", "BASE_CURRENCY_MISMATCH" ->
                    new BigDecimal("0.20");
            case "ESTIMATED", "UNCLEAR", "USER_SELECTION_REQUIRED" -> new BigDecimal("0.10");
            case "POSSIBLE_DUPLICATE" -> BigDecimal.ZERO;
            default -> new BigDecimal("0.10");
        };
    }
}
