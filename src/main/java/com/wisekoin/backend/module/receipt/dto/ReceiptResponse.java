package com.wisekoin.backend.module.receipt.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record ReceiptResponse(
        @JsonProperty("store_name") String storeName,
        @JsonProperty("category_id") Long categoryId,
        @JsonProperty("category") String category,
        @JsonProperty("amount") BigDecimal amount) {}
