package com.wisekoin.backend.module.ai.adaptive;

import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class MerchantNormalizer {
    private static final int MAX_KEY_LENGTH = 200;

    public String normalize(String merchant) {
        if (merchant == null || merchant.isBlank()) {
            return "";
        }
        String normalized =
                Normalizer.normalize(merchant, Normalizer.Form.NFD)
                        .replaceAll("\\p{M}+", "")
                        .toLowerCase(Locale.ROOT)
                        .replace('đ', 'd')
                        .replaceAll("[^\\p{L}\\p{N}]+", " ")
                        .trim()
                        .replaceAll("\\s+", " ");
        return normalized.substring(0, Math.min(normalized.length(), MAX_KEY_LENGTH));
    }
}
