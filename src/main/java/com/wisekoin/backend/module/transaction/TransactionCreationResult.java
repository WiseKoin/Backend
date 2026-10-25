package com.wisekoin.backend.module.transaction;

public record TransactionCreationResult(Transaction transaction, boolean replayed) {}
