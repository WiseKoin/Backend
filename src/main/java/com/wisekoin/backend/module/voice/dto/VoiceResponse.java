package com.wisekoin.backend.module.voice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record VoiceResponse(
        BigDecimal amount,
        String currency,
        String type,
        @JsonProperty("category_id") Long categoryId,
        String category,
        String note,
        String transcript) {}
