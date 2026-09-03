package com.wisekoin.backend.module.receipt.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReceiptResponse(
    @JsonProperty("store_name")
    String storeName,

    @JsonProperty("category")
    String category,

    @JsonProperty("amount")
    Double amount
) {}