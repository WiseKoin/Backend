package com.wisekoin.backend.module.ai.adaptive;

import com.wisekoin.backend.module.category.Category;
import com.wisekoin.backend.module.category.CategoryRepository;
import com.wisekoin.backend.module.common.exception.BadRequestException;
import com.wisekoin.backend.module.common.exception.ResourceNotFoundException;
import com.wisekoin.backend.module.transaction.Transaction;
import com.wisekoin.backend.module.user.UserRepository;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiAdaptationService {
    private final AiExtractionRepository extractionRepository;
    private final AiFeedbackRepository feedbackRepository;
    private final CategorizationRuleRepository ruleRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final MerchantNormalizer merchantNormalizer;
    private final Clock clock;

    public Optional<Category> findMatchingRule(
            Long userId, String merchant, String transactionType, List<Category> validCategories) {
        String merchantKey = merchantNormalizer.normalize(merchant);
        if (merchantKey.isBlank() || !isRuleType(transactionType)) {
            return Optional.empty();
        }
        return ruleRepository
                .findByUserIdAndMerchantKeyAndTransactionType(userId, merchantKey, transactionType)
                .flatMap(
                        rule ->
                                validCategories.stream()
                                        .filter(
                                                category ->
                                                        category.getId()
                                                                .equals(rule.getCategoryId()))
                                        .filter(
                                                category ->
                                                        category.getType()
                                                                == expectedCategoryType(
                                                                        transactionType))
                                        .findFirst());
    }

    public String formatRules(Long userId, String transactionType, List<Category> validCategories) {
        if (!isRuleType(transactionType)) {
            return "none";
        }
        var categoriesById =
                validCategories.stream()
                        .filter(
                                category ->
                                        category.getType() == expectedCategoryType(transactionType))
                        .collect(Collectors.toMap(Category::getId, category -> category));
        String rules =
                ruleRepository
                        .findTop50ByUserIdAndTransactionTypeOrderByConfirmationCountDescUpdatedAtDesc(
                                userId, transactionType)
                        .stream()
                        .filter(rule -> categoriesById.containsKey(rule.getCategoryId()))
                        .map(
                                rule ->
                                        rule.getMerchantKey()
                                                + " -> "
                                                + rule.getCategoryId()
                                                + " ("
                                                + categoriesById.get(rule.getCategoryId()).getName()
                                                + ")")
                        .collect(Collectors.joining("; "));
        return rules.isBlank() ? "none" : rules;
    }

    @Transactional
    public UUID recordExtraction(AiExtractionRecordRequest request) {
        Instant now = clock.instant();
        AiExtraction extraction = new AiExtraction();
        extraction.setId(UUID.randomUUID());
        extraction.setUser(userRepository.getReferenceById(request.userId()));
        extraction.setSource(limit(request.source(), 20));
        extraction.setMerchantKey(merchantNormalizer.normalize(request.merchant()));
        extraction.setMerchantLabel(limit(request.merchant(), 200));
        extraction.setProposedCategoryId(request.proposedCategoryId());
        extraction.setTransactionType(limit(request.transactionType(), 20));
        extraction.setAmount(request.amount());
        extraction.setCurrency(limit(request.currency(), 3));
        extraction.setPromptVersion(limit(request.promptVersion(), 100));
        extraction.setModelVersion(limit(request.modelVersion(), 150));
        extraction.setProvider(limit(request.provider(), 50));
        extraction.setConfidenceScore(request.confidence().score());
        extraction.setConfidenceLevel(limit(request.confidence().level(), 20));
        extraction.setConfidenceReasons(
                limit(String.join(",", request.confidence().reasons()), 1000));
        extraction.setConfidenceProfile(limit(request.confidence().profile(), 100));
        extraction.setConfidenceCalibrated(request.confidence().calibrated());
        extraction.setRuleApplied(request.ruleApplied());
        extraction.setRequiresClarification(request.requiresClarification());
        extraction.setLocale(limit(request.locale(), 35));
        extraction.setTimeZone(limit(request.timeZone(), 100));
        extraction.setStatus("PENDING");
        extraction.setCreatedAt(now);
        return extractionRepository.save(extraction).getId();
    }

    @Transactional
    public void confirmExtraction(
            Long userId,
            UUID extractionId,
            Long confirmedCategoryId,
            Transaction.TransactionType confirmedType,
            boolean rememberRule) {
        if (extractionId == null) {
            return;
        }
        AiExtraction extraction =
                extractionRepository
                        .findByIdAndUserId(extractionId, userId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "AI extraction suggestion not found"));
        if (!"PENDING".equals(extraction.getStatus())) {
            throw new BadRequestException("AI extraction suggestion was already confirmed");
        }

        Category confirmedCategory =
                confirmedCategoryId == null
                        ? null
                        : categoryRepository
                                .findById(confirmedCategoryId)
                                .filter(category -> category.getUser().getId().equals(userId))
                                .orElseThrow(
                                        () ->
                                                new ResourceNotFoundException(
                                                        "Confirmed category not found"));
        validateRuleCategory(confirmedCategory, confirmedType);

        Instant now = clock.instant();
        AiFeedback feedback = new AiFeedback();
        feedback.setExtraction(extraction);
        feedback.setUser(userRepository.getReferenceById(userId));
        feedback.setProposedCategoryId(extraction.getProposedCategoryId());
        feedback.setConfirmedCategoryId(confirmedCategoryId);
        feedback.setConfirmedTransactionType(confirmedType.name());
        feedback.setOutcome(
                Objects.equals(extraction.getProposedCategoryId(), confirmedCategoryId)
                                && extraction.getTransactionType().equals(confirmedType.name())
                        ? "ACCEPTED"
                        : "CORRECTED");
        boolean canRemember =
                rememberRule
                        && confirmedCategory != null
                        && !extraction.getMerchantKey().isBlank()
                        && isRuleType(confirmedType.name());
        feedback.setRememberRule(canRemember);
        feedback.setCreatedAt(now);
        feedbackRepository.save(feedback);

        if (canRemember) {
            upsertRule(
                    userId,
                    extraction.getMerchantKey(),
                    confirmedType.name(),
                    confirmedCategoryId,
                    now);
        }
        extraction.setStatus("CONFIRMED");
        extraction.setConfirmedAt(now);
    }

    private void upsertRule(
            Long userId, String merchantKey, String transactionType, Long categoryId, Instant now) {
        CategorizationRule rule =
                ruleRepository
                        .findByUserIdAndMerchantKeyAndTransactionType(
                                userId, merchantKey, transactionType)
                        .orElseGet(
                                () -> {
                                    CategorizationRule created = new CategorizationRule();
                                    created.setUser(userRepository.getReferenceById(userId));
                                    created.setMerchantKey(merchantKey);
                                    created.setTransactionType(transactionType);
                                    created.setConfirmationCount(0);
                                    created.setCreatedAt(now);
                                    return created;
                                });
        rule.setCategoryId(categoryId);
        rule.setConfirmationCount(rule.getConfirmationCount() + 1);
        rule.setUpdatedAt(now);
        ruleRepository.save(rule);
    }

    private void validateRuleCategory(
            Category category, Transaction.TransactionType transactionType) {
        if (transactionType == Transaction.TransactionType.TRANSFER) {
            if (category != null) {
                throw new BadRequestException("Transfers cannot be remembered as category rules");
            }
            return;
        }
        if (category == null) {
            throw new BadRequestException("A category is required for AI feedback");
        }
        Category.CategoryType expected = expectedCategoryType(transactionType.name());
        if (expected == null || category.getType() != expected) {
            throw new BadRequestException("Confirmed category does not match transaction type");
        }
    }

    private boolean isRuleType(String transactionType) {
        return "EXPENSE".equals(transactionType) || "INCOME".equals(transactionType);
    }

    private Category.CategoryType expectedCategoryType(String transactionType) {
        return switch (transactionType) {
            case "EXPENSE" -> Category.CategoryType.EXPENSE;
            case "INCOME" -> Category.CategoryType.INCOME;
            default -> null;
        };
    }

    private String limit(String value, int length) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.substring(0, Math.min(trimmed.length(), length));
    }
}
