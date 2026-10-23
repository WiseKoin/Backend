package com.wisekoin.backend.module.ai.adaptive;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MerchantNormalizerTest {
    private final MerchantNormalizer normalizer = new MerchantNormalizer();

    @Test
    void normalizesVietnameseDiacriticsPunctuationAndWhitespace() {
        assertEquals("ca phe sua da 123", normalizer.normalize("  Cà-phê SỮA ĐÁ! 123  "));
    }

    @Test
    void blankMerchantProducesNoRuleKey() {
        assertEquals("", normalizer.normalize("  "));
    }
}
