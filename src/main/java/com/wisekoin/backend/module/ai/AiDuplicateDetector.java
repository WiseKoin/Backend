package com.wisekoin.backend.module.ai;

import com.wisekoin.backend.module.ai.dto.AiDuplicateCandidate;
import com.wisekoin.backend.module.transaction.Transaction;
import com.wisekoin.backend.module.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiDuplicateDetector {
    private static final int SEARCH_WINDOW_DAYS = 2;
    private static final int MAX_CANDIDATES = 5;

    private final TransactionRepository transactionRepository;
    private final Clock clock;

    public List<AiDuplicateCandidate> findCandidates(
            Long userId,
            BigDecimal amount,
            String type,
            LocalDateTime occurredAt,
            String description) {
        Transaction.TransactionType transactionType = parseType(type);
        if (amount == null || amount.signum() <= 0 || transactionType == null) {
            return List.of();
        }

        LocalDateTime referenceTime = occurredAt != null ? occurredAt : LocalDateTime.now(clock);
        List<Transaction> transactions =
                transactionRepository.findAllByUserIdAndTransactionDateBetween(
                        userId,
                        referenceTime.minusDays(SEARCH_WINDOW_DAYS),
                        referenceTime.plusDays(SEARCH_WINDOW_DAYS));

        return transactions.stream()
                .filter(transaction -> transaction.getAmount().compareTo(amount) == 0)
                .filter(transaction -> transaction.getType() == transactionType)
                .sorted(
                        (left, right) ->
                                durationFrom(left, referenceTime)
                                        .compareTo(durationFrom(right, referenceTime)))
                .limit(MAX_CANDIDATES)
                .map(
                        transaction ->
                                new AiDuplicateCandidate(
                                        transaction.getId(),
                                        transaction.getAmount(),
                                        transaction.getType().name(),
                                        transaction.getTransactionDate(),
                                        matchReasons(transaction, description)))
                .toList();
    }

    private Duration durationFrom(Transaction transaction, LocalDateTime referenceTime) {
        return Duration.between(transaction.getTransactionDate(), referenceTime).abs();
    }

    private List<String> matchReasons(Transaction transaction, String description) {
        List<String> reasons = new ArrayList<>(List.of("EXACT_AMOUNT", "SAME_TYPE", "NEARBY_DATE"));
        if (descriptionsMatch(transaction.getNote(), description)) {
            reasons.add("SIMILAR_DESCRIPTION");
        }
        return List.copyOf(reasons);
    }

    private boolean descriptionsMatch(String existing, String extracted) {
        String normalizedExisting = normalize(existing);
        String normalizedExtracted = normalize(extracted);
        return !normalizedExisting.isBlank()
                && !normalizedExtracted.isBlank()
                && (normalizedExisting.contains(normalizedExtracted)
                        || normalizedExtracted.contains(normalizedExisting));
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private Transaction.TransactionType parseType(String type) {
        if (type == null) {
            return null;
        }
        try {
            return Transaction.TransactionType.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
