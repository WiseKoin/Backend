package com.wisekoin.backend.module.receipt;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReceiptResponseDto(
    @JsonProperty("store_name")
    String storeName,

    @JsonProperty("category")
    String category,

    @JsonProperty("amount")
    Double amount
) {}