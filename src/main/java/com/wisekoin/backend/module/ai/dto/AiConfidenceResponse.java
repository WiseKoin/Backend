package com.wisekoin.backend.module.ai.dto;

import java.math.BigDecimal;
import java.util.List;

public record AiConfidenceResponse(
        BigDecimal score, String level, List<String> reasons, String profile, boolean calibrated) {}
