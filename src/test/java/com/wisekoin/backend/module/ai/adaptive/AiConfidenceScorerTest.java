package com.wisekoin.backend.module.ai.adaptive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wisekoin.backend.module.ai.dto.AiFieldAmbiguity;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiConfidenceScorerTest {
    private final AiConfidenceScorer scorer =
            new AiConfidenceScorer(
                    "baseline-v1", false, new BigDecimal("0.80"), new BigDecimal("0.55"));

    @Test
    void derivesUncalibratedConfidenceFromApplicationSignals() {
        var confidence =
                scorer.score(
                        List.of(new AiFieldAmbiguity("category_id", "UNRESOLVED")), true, false);

        assertEquals(new BigDecimal("0.5500"), confidence.score());
        assertEquals("MEDIUM", confidence.level());
        assertFalse(confidence.calibrated());
        assertTrue(confidence.reasons().contains("AMBIGUITY_UNRESOLVED"));
        assertTrue(confidence.reasons().contains("POSSIBLE_DUPLICATE"));
    }

    @Test
    void confirmedRuleIsAnExplicitPositiveSignal() {
        var confidence = scorer.score(List.of(), false, true);

        assertEquals(new BigDecimal("1.0000"), confidence.score());
        assertEquals("HIGH", confidence.level());
        assertEquals(List.of("CONFIRMED_MERCHANT_RULE"), confidence.reasons());
    }
}
