package com.wisekoin.backend.module.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiCategoryCandidate(
        @JsonProperty("category_id") Long categoryId, String category, int rank) {}
