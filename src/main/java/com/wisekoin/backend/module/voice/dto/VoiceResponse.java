package com.wisekoin.backend.module.voice.dto;

import java.math.BigDecimal;

public record VoiceResponse(
        BigDecimal amount, String type, String category, String note, String transcript) {}
