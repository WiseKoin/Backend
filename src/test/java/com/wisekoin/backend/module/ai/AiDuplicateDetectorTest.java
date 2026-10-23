package com.wisekoin.backend.module.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wisekoin.backend.module.ai.dto.AiDuplicateCandidate;
import com.wisekoin.backend.module.transaction.Transaction;
import com.wisekoin.backend.module.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiDuplicateDetectorTest {
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC);
    private final AiDuplicateDetector detector =
            new AiDuplicateDetector(transactionRepository, clock);

    @Test
    void findsOnlyExactAmountAndTypeCandidatesWithinUserScopedWindow() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 10, 23, 9, 0);
        Transaction matching = transaction(1L, "50000.00", "EXPENSE", "Lunch", occurredAt);
        Transaction wrongAmount = transaction(2L, "60000.00", "EXPENSE", "Lunch", occurredAt);
        Transaction wrongType = transaction(3L, "50000.00", "INCOME", "Lunch", occurredAt);
        when(transactionRepository.findAllByUserIdAndTransactionDateBetween(
                        eq(42L), eq(occurredAt.minusDays(2)), eq(occurredAt.plusDays(2))))
                .thenReturn(List.of(wrongAmount, wrongType, matching));

        List<AiDuplicateCandidate> candidates =
                detector.findCandidates(
                        42L, new BigDecimal("50000"), "EXPENSE", occurredAt, "Lunch payment");

        assertEquals(1, candidates.size());
        assertEquals(1L, candidates.getFirst().transactionId());
        assertTrue(candidates.getFirst().matchReasons().contains("SIMILAR_DESCRIPTION"));
    }

    @Test
    void skipsDuplicateSearchForUnknownTransactionType() {
        assertTrue(
                detector.findCandidates(
                                42L,
                                new BigDecimal("50000"),
                                "UNKNOWN",
                                LocalDateTime.now(clock),
                                "Unknown")
                        .isEmpty());
    }

    private Transaction transaction(
            Long id, String amount, String type, String note, LocalDateTime transactionDate) {
        Transaction transaction = new Transaction();
        transaction.setId(id);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setType(Transaction.TransactionType.valueOf(type));
        transaction.setNote(note);
        transaction.setTransactionDate(transactionDate);
        return transaction;
    }
}
