package com.wisekoin.backend.module.ai.suggestion;

import com.wisekoin.backend.module.ai.adaptive.AiExtraction;
import com.wisekoin.backend.module.ai.adaptive.AiExtractionRepository;
import com.wisekoin.backend.module.ai.adaptive.MerchantNormalizer;
import com.wisekoin.backend.module.ai.dto.AiFieldAmbiguity;
import com.wisekoin.backend.module.ai.suggestion.dto.AiSuggestionClarificationRequest;
import com.wisekoin.backend.module.ai.suggestion.dto.AiSuggestionConfirmationRequest;
import com.wisekoin.backend.module.ai.suggestion.dto.AiSuggestionRejectionRequest;
import com.wisekoin.backend.module.ai.suggestion.dto.AiSuggestionResponse;
import com.wisekoin.backend.module.category.Category;
import com.wisekoin.backend.module.category.CategoryRepository;
import com.wisekoin.backend.module.common.exception.BadRequestException;
import com.wisekoin.backend.module.common.exception.ResourceNotFoundException;
import com.wisekoin.backend.module.transaction.Transaction;
import com.wisekoin.backend.module.transaction.TransactionService;
import com.wisekoin.backend.module.transaction.dto.TransactionRequest;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserRepository;
import com.wisekoin.backend.module.wallet.WalletRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiSuggestionService {
    private static final Set<String> STATUSES = Set.of("PENDING", "CONFIRMED", "REJECTED");

    private final AiExtractionRepository extractionRepository;
    private final AiSuggestionActionRepository actionRepository;
    private final CategoryRepository categoryRepository;
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final TransactionService transactionService;
    private final MerchantNormalizer merchantNormalizer;
    private final Clock clock;

    @Transactional
    public AiSuggestionConfirmationResult confirm(
            Long userId, UUID suggestionId, AiSuggestionConfirmationRequest request) {
        AiExtraction suggestion = getPendingForUpdate(userId, suggestionId);
        Transaction.TransactionType type =
                request.getType() != null
                        ? request.getType()
                        : parseTransactionType(suggestion.getTransactionType());
        BigDecimal amount =
                request.getAmount() != null ? request.getAmount() : suggestion.getAmount();
        Long walletId =
                request.getWalletId() != null
                        ? request.getWalletId()
                        : suggestion.getSourceWalletId();
        Long destinationWalletId =
                type == Transaction.TransactionType.TRANSFER
                        ? request.getDestinationWalletId() != null
                                ? request.getDestinationWalletId()
                                : suggestion.getDestinationWalletId()
                        : null;
        Long categoryId =
                type == Transaction.TransactionType.TRANSFER
                        ? null
                        : request.getCategoryId() != null
                                ? request.getCategoryId()
                                : suggestion.getProposedCategoryId();

        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("A valid amount is required to confirm the suggestion");
        }
        if (walletId == null) {
            throw new BadRequestException("A wallet is required to confirm the suggestion");
        }

        TransactionRequest transactionRequest = new TransactionRequest();
        transactionRequest.setWalletId(walletId);
        transactionRequest.setDestinationWalletId(destinationWalletId);
        transactionRequest.setCategoryId(categoryId);
        transactionRequest.setAmount(amount);
        transactionRequest.setType(type);
        transactionRequest.setNote(request.getNote());
        transactionRequest.setTransactionDate(
                request.getTransactionDate() != null
                        ? request.getTransactionDate()
                        : suggestion.getOccurredAt());
        transactionRequest.setAiExtractionId(suggestionId);
        transactionRequest.setRememberAiCategory(request.isRememberAiCategory());

        Transaction transaction = transactionService.createTransaction(userId, transactionRequest);
        AiSuggestionResponse response =
                toResponse(
                        extractionRepository
                                .findOneByIdAndUserId(suggestionId, userId)
                                .orElseThrow());
        return new AiSuggestionConfirmationResult(response, transaction);
    }

    @Transactional
    public AiSuggestionResponse clarify(
            Long userId, UUID suggestionId, AiSuggestionClarificationRequest request) {
        if (!request.hasChanges()) {
            throw new BadRequestException("At least one clarification is required");
        }
        AiExtraction suggestion = getPendingForUpdate(userId, suggestionId);
        List<AiFieldAmbiguity> ambiguities = parseAmbiguities(suggestion.getAmbiguityReasons());

        if (request.getAmount() != null) {
            suggestion.setAmount(request.getAmount());
            removeAmbiguities(ambiguities, Set.of("amount", "total"));
        }
        if (request.getCurrency() != null) {
            String currency = request.getCurrency().trim().toUpperCase(Locale.ROOT);
            String baseCurrency =
                    userRepository
                            .findById(userId)
                            .map(User::getBaseCurrency)
                            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            if (!currency.equalsIgnoreCase(baseCurrency)) {
                throw new BadRequestException(
                        "Suggestion currency must match the user's base currency");
            }
            suggestion.setCurrency(currency);
            removeAmbiguities(ambiguities, Set.of("currency"));
        }
        if (request.getType() != null) {
            suggestion.setTransactionType(request.getType().name());
            removeAmbiguities(ambiguities, Set.of("type", "transaction_type"));
        }
        if (request.isCategoryIdSet()) {
            suggestion.setProposedCategoryId(request.getCategoryId());
            removeAmbiguities(ambiguities, Set.of("category", "category_id"));
        }
        if (request.isOccurredAtSet()) {
            suggestion.setOccurredAt(request.getOccurredAt());
            removeAmbiguities(ambiguities, Set.of("date", "occurred_at"));
        }
        if (request.isSourceWalletIdSet()) {
            suggestion.setSourceWalletId(request.getSourceWalletId());
            removeAmbiguities(ambiguities, Set.of("source_wallet", "source_wallet_id"));
        }
        if (request.isDestinationWalletIdSet()) {
            suggestion.setDestinationWalletId(request.getDestinationWalletId());
            removeAmbiguities(ambiguities, Set.of("destination_wallet", "destination_wallet_id"));
        }
        if (request.isMerchantSet()) {
            String merchant = limit(request.getMerchant(), 200);
            suggestion.setMerchantLabel(merchant);
            suggestion.setMerchantKey(merchantNormalizer.normalize(merchant));
            removeAmbiguities(ambiguities, Set.of("merchant", "store_name", "counterparty"));
        }
        if (Boolean.TRUE.equals(request.getAcknowledgePossibleDuplicate())) {
            ambiguities.removeIf(
                    ambiguity ->
                            "transaction".equals(ambiguity.field())
                                    && "POSSIBLE_DUPLICATE".equals(ambiguity.code()));
        }

        validateClarifiedSuggestion(userId, suggestion, ambiguities);
        Instant now = clock.instant();
        suggestion.setAmbiguityReasons(formatAmbiguities(ambiguities));
        suggestion.setRequiresClarification(!ambiguities.isEmpty());
        suggestion.setClarified(true);
        suggestion.setUpdatedAt(now);
        actionRepository.save(toAction(suggestion, userId, "CLARIFIED", null, now));
        return toResponse(suggestion);
    }

    @Transactional
    public AiSuggestionResponse reject(
            Long userId, UUID suggestionId, AiSuggestionRejectionRequest request) {
        AiExtraction suggestion = getPendingForUpdate(userId, suggestionId);
        Instant now = clock.instant();
        suggestion.setStatus("REJECTED");
        suggestion.setRejectionReason(request.reason().name());
        suggestion.setRejectedAt(now);
        suggestion.setUpdatedAt(now);
        actionRepository.save(
                toAction(suggestion, userId, "REJECTED", request.reason().name(), now));
        return toResponse(suggestion);
    }

    @Transactional
    public AiSuggestionResponse get(Long userId, UUID suggestionId) {
        return toResponse(
                extractionRepository
                        .findOneByIdAndUserId(suggestionId, userId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "AI suggestion not found or access denied")));
    }

    @Transactional
    public Page<AiSuggestionResponse> list(Long userId, String status, Pageable pageable) {
        String normalizedStatus = status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalizedStatus)) {
            throw new BadRequestException("Unsupported AI suggestion status");
        }
        return extractionRepository
                .findAllByUserIdAndStatus(userId, normalizedStatus, pageable)
                .map(this::toResponse);
    }

    private AiExtraction getPendingForUpdate(Long userId, UUID suggestionId) {
        AiExtraction suggestion =
                extractionRepository
                        .findByIdAndUserId(suggestionId, userId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "AI suggestion not found or access denied"));
        if (!"PENDING".equals(suggestion.getStatus())) {
            throw new BadRequestException("AI suggestion is no longer pending");
        }
        return suggestion;
    }

    private void validateClarifiedSuggestion(
            Long userId, AiExtraction suggestion, List<AiFieldAmbiguity> ambiguities) {
        Transaction.TransactionType type =
                parseOptionalTransactionType(suggestion.getTransactionType());
        if (type == null) {
            addAmbiguity(ambiguities, "type", "UNRESOLVED");
        }

        Long categoryId = suggestion.getProposedCategoryId();
        if (type == Transaction.TransactionType.TRANSFER) {
            suggestion.setProposedCategoryId(null);
            removeAmbiguities(ambiguities, Set.of("category", "category_id"));
        } else if (type != null) {
            if (categoryId == null) {
                addAmbiguity(ambiguities, "category_id", "UNRESOLVED");
            } else {
                Category category = getOwnedCategory(userId, categoryId);
                Category.CategoryType expected =
                        type == Transaction.TransactionType.EXPENSE
                                ? Category.CategoryType.EXPENSE
                                : Category.CategoryType.INCOME;
                if (category.getType() != expected) {
                    throw new BadRequestException(
                            "Clarified category does not match the transaction type");
                }
            }
        }

        validateOwnedWallet(userId, suggestion.getSourceWalletId());
        validateOwnedWallet(userId, suggestion.getDestinationWalletId());
        if (type == Transaction.TransactionType.TRANSFER) {
            if (suggestion.getSourceWalletId() == null) {
                addAmbiguity(ambiguities, "source_wallet_id", "UNRESOLVED");
            }
            if (suggestion.getDestinationWalletId() == null) {
                addAmbiguity(ambiguities, "destination_wallet_id", "UNRESOLVED");
            }
            if (suggestion.getSourceWalletId() != null
                    && suggestion.getSourceWalletId().equals(suggestion.getDestinationWalletId())) {
                throw new BadRequestException("Transfer wallets must be different");
            }
        } else if (type != null) {
            suggestion.setDestinationWalletId(null);
            removeAmbiguities(
                    ambiguities,
                    Set.of(
                            "source_wallet",
                            "source_wallet_id",
                            "destination_wallet",
                            "destination_wallet_id",
                            "wallets"));
        }

        if (suggestion.getOccurredAt() == null) {
            addAmbiguity(ambiguities, "occurred_at", "MISSING");
        } else {
            LocalDateTime now = LocalDateTime.now(clock.withZone(resolveZone(suggestion)));
            if (suggestion.getOccurredAt().isAfter(now.plusMinutes(5))
                    || suggestion.getOccurredAt().isBefore(now.minusYears(20))) {
                throw new BadRequestException("Clarified occurrence date is out of range");
            }
        }
    }

    private Category getOwnedCategory(Long userId, Long categoryId) {
        return categoryRepository
                .findById(categoryId)
                .filter(category -> category.getUser().getId().equals(userId))
                .orElseThrow(
                        () -> new ResourceNotFoundException("Category not found or access denied"));
    }

    private void validateOwnedWallet(Long userId, Long walletId) {
        if (walletId == null) {
            return;
        }
        walletRepository
                .findById(walletId)
                .filter(wallet -> wallet.getUser().getId().equals(userId))
                .orElseThrow(
                        () -> new ResourceNotFoundException("Wallet not found or access denied"));
    }

    private Transaction.TransactionType parseTransactionType(String value) {
        Transaction.TransactionType type = parseOptionalTransactionType(value);
        if (type == null) {
            throw new BadRequestException("Transaction type must be clarified before confirmation");
        }
        return type;
    }

    private Transaction.TransactionType parseOptionalTransactionType(String value) {
        try {
            return Transaction.TransactionType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }

    private AiSuggestionAction toAction(
            AiExtraction suggestion, Long userId, String actionName, String reason, Instant now) {
        AiSuggestionAction action = new AiSuggestionAction();
        action.setExtraction(suggestion);
        action.setUser(userRepository.getReferenceById(userId));
        action.setAction(actionName);
        action.setAmount(suggestion.getAmount());
        action.setCurrency(suggestion.getCurrency());
        action.setTransactionType(suggestion.getTransactionType());
        action.setCategoryId(suggestion.getProposedCategoryId());
        action.setOccurredAt(suggestion.getOccurredAt());
        action.setSourceWalletId(suggestion.getSourceWalletId());
        action.setDestinationWalletId(suggestion.getDestinationWalletId());
        action.setReasonCode(reason);
        action.setCreatedAt(now);
        return action;
    }

    private AiSuggestionResponse toResponse(AiExtraction suggestion) {
        Instant resolvedAt =
                "CONFIRMED".equals(suggestion.getStatus())
                        ? suggestion.getConfirmedAt()
                        : suggestion.getRejectedAt();
        return new AiSuggestionResponse(
                suggestion.getId(),
                suggestion.getSource(),
                suggestion.getAmount(),
                suggestion.getCurrency(),
                suggestion.getTransactionType(),
                suggestion.getMerchantLabel(),
                suggestion.getOccurredAt(),
                suggestion.getProposedCategoryId(),
                suggestion.getSourceWalletId(),
                suggestion.getDestinationWalletId(),
                suggestion.isRequiresClarification(),
                parseAmbiguities(suggestion.getAmbiguityReasons()),
                suggestion.getStatus(),
                suggestion.getRejectionReason(),
                suggestion.getTransactionId(),
                suggestion.getPromptVersion(),
                suggestion.getModelVersion(),
                suggestion.getProvider(),
                suggestion.getLocale(),
                suggestion.getTimeZone(),
                suggestion.getCreatedAt(),
                suggestion.getUpdatedAt(),
                resolvedAt);
    }

    private List<AiFieldAmbiguity> parseAmbiguities(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(
                List.of(encoded.split(",")).stream()
                        .map(value -> value.split(":", 2))
                        .filter(parts -> parts.length == 2)
                        .map(parts -> new AiFieldAmbiguity(parts[0], parts[1]))
                        .distinct()
                        .toList());
    }

    private String formatAmbiguities(List<AiFieldAmbiguity> ambiguities) {
        return ambiguities.stream()
                .map(ambiguity -> ambiguity.field() + ":" + ambiguity.code())
                .distinct()
                .collect(Collectors.joining(","));
    }

    private void removeAmbiguities(
            List<AiFieldAmbiguity> ambiguities, Set<String> normalizedFields) {
        ambiguities.removeIf(
                ambiguity -> normalizedFields.contains(ambiguity.field().toLowerCase(Locale.ROOT)));
    }

    private void addAmbiguity(List<AiFieldAmbiguity> ambiguities, String field, String code) {
        AiFieldAmbiguity ambiguity = new AiFieldAmbiguity(field, code);
        if (!ambiguities.contains(ambiguity)) {
            ambiguities.add(ambiguity);
        }
    }

    private ZoneId resolveZone(AiExtraction suggestion) {
        try {
            return ZoneId.of(suggestion.getTimeZone());
        } catch (RuntimeException exception) {
            return ZoneOffset.UTC;
        }
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.substring(0, Math.min(trimmed.length(), maxLength));
    }
}
