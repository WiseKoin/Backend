package com.wisekoin.backend.module.receipt;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wisekoin.backend.module.ai.AiDuplicateDetector;
import com.wisekoin.backend.module.ai.adaptive.AiAdaptationService;
import com.wisekoin.backend.module.ai.adaptive.AiConfidenceScorer;
import com.wisekoin.backend.module.ai.adaptive.AiExtractionRecordRequest;
import com.wisekoin.backend.module.ai.context.AiRequestContext;
import com.wisekoin.backend.module.ai.context.AiRequestContextResolver;
import com.wisekoin.backend.module.ai.dto.AiCategoryCandidate;
import com.wisekoin.backend.module.ai.dto.AiConfidenceResponse;
import com.wisekoin.backend.module.ai.dto.AiDuplicateCandidate;
import com.wisekoin.backend.module.ai.dto.AiFieldAmbiguity;
import com.wisekoin.backend.module.ai.ingestion.AiIngestionRequestService;
import com.wisekoin.backend.module.ai.media.AiMediaIngestionService;
import com.wisekoin.backend.module.ai.media.AiMediaPayload;
import com.wisekoin.backend.module.ai.prompt.AiPromptService;
import com.wisekoin.backend.module.ai.prompt.VersionedPrompt;
import com.wisekoin.backend.module.ai.provider.AiExtractionClient;
import com.wisekoin.backend.module.ai.provider.AiExtractionRequest;
import com.wisekoin.backend.module.ai.provider.AiExtractionResult;
import com.wisekoin.backend.module.category.Category;
import com.wisekoin.backend.module.category.CategoryService;
import com.wisekoin.backend.module.receipt.dto.ReceiptResponse;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserService;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class ReceiptService {
    static final String UNKNOWN_CATEGORY_ID = "UNKNOWN";
    private static final Set<String> PAYMENT_METHODS =
            Set.of("CASH", "CARD", "BANK_TRANSFER", "E_WALLET", "OTHER", "UNKNOWN");
    private static final BigDecimal ARITHMETIC_TOLERANCE = new BigDecimal("0.01");
    private static final int MAX_PROMPT_CONTEXT_ENTRIES = 200;

    private final AiExtractionClient extractionClient;
    private final AiPromptService promptService;
    private final AiRequestContextResolver contextResolver;
    private final AiAdaptationService adaptationService;
    private final AiConfidenceScorer confidenceScorer;
    private final AiMediaIngestionService mediaIngestionService;
    private final AiIngestionRequestService ingestionRequestService;
    private final JsonMapper jsonMapper;
    private final CategoryService categoryService;
    private final UserService userService;
    private final AiDuplicateDetector duplicateDetector;
    private final Clock clock;

    public ReceiptResponse extractReceipt(MultipartFile file, Long userId) throws IOException {
        return extractReceipt(file, userId, null, null, null);
    }

    public ReceiptResponse extractReceipt(
            MultipartFile file, Long userId, String locale, String timeZone) throws IOException {
        return extractReceipt(file, userId, locale, timeZone, null);
    }

    public ReceiptResponse extractReceipt(
            MultipartFile file, Long userId, String locale, String timeZone, String idempotencyKey)
            throws IOException {
        AiMediaPayload media = mediaIngestionService.ingestReceipt(file);
        var attempt =
                ingestionRequestService.begin(
                        userId, "RECEIPT", idempotencyKey, media.fingerprint());
        try {
            ReceiptResponse response =
                    extractReceipt(media, userId, locale, timeZone, attempt.requestId());
            ingestionRequestService.complete(attempt.requestId(), response.extractionId());
            return response;
        } catch (IOException | RuntimeException exception) {
            ingestionRequestService.fail(attempt.requestId());
            throw exception;
        }
    }

    private ReceiptResponse extractReceipt(
            AiMediaPayload media, Long userId, String locale, String timeZone, UUID requestId)
            throws IOException {
        List<Category> expenseCategories = getExpenseCategories(userId);
        String currency = getBaseCurrency(userId);
        AiRequestContext context = contextResolver.resolve(locale, timeZone);
        VersionedPrompt prompt =
                promptService.receipt(
                        Map.of(
                                "CURRENT_DATETIME", context.now(clock).toString(),
                                "LOCALE", context.localeTag(),
                                "TIME_ZONE", context.timeZone(),
                                "BASE_CURRENCY", currency,
                                "CATEGORIES", formatCategories(expenseCategories),
                                "MERCHANT_RULES",
                                        adaptationService.formatRules(
                                                userId, "EXPENSE", expenseCategories)));
        AiExtractionResult result =
                extractionClient.extract(
                        new AiExtractionRequest(
                                prompt.content(),
                                media.mimeType(),
                                media.base64Content(),
                                buildResponseSchema(expenseCategories),
                                0.0));

        return parseExtraction(
                result.content(),
                expenseCategories,
                userId,
                currency,
                context,
                prompt.version(),
                result,
                requestId);
    }

    private List<Category> getExpenseCategories(Long userId) {
        return categoryService.getCategoryByType(Category.CategoryType.EXPENSE, userId);
    }

    private String getBaseCurrency(Long userId) {
        User user = userService.getUserById(userId);
        if (user.getBaseCurrency() == null || user.getBaseCurrency().isBlank()) {
            return "VND";
        }
        return user.getBaseCurrency().trim().toUpperCase(Locale.ROOT);
    }

    Map<String, Object> buildResponseSchema(List<Category> categories) {
        List<String> categoryIds =
                new ArrayList<>(
                        categories.stream().map(category -> category.getId().toString()).toList());
        categoryIds.add(UNKNOWN_CATEGORY_ID);

        Map<String, Object> categorySchema = new HashMap<>();
        categorySchema.put("type", "STRING");
        categorySchema.put("enum", categoryIds);
        categorySchema.put(
                "description",
                "Stable ID of an available expense category, or UNKNOWN when no category applies.");

        Map<String, Object> categoryCandidatesSchema = new HashMap<>();
        categoryCandidatesSchema.put("type", "ARRAY");
        categoryCandidatesSchema.put("items", categorySchema);
        categoryCandidatesSchema.put("maxItems", 3);
        categoryCandidatesSchema.put(
                "description", "Category IDs ranked from best to worst without duplicates.");

        Map<String, Object> ambiguityProperties = new HashMap<>();
        ambiguityProperties.put(
                "field",
                Map.of(
                        "type",
                        "STRING",
                        "enum",
                        List.of(
                                "store_name",
                                "amount",
                                "currency",
                                "occurred_at",
                                "subtotal",
                                "tax",
                                "discount",
                                "tip",
                                "payment_method",
                                "category_id")));
        ambiguityProperties.put(
                "code",
                Map.of(
                        "type",
                        "STRING",
                        "enum",
                        List.of("MISSING", "UNCLEAR", "CONFLICTING", "ESTIMATED")));
        Map<String, Object> ambiguityItemSchema = new HashMap<>();
        ambiguityItemSchema.put("type", "OBJECT");
        ambiguityItemSchema.put("properties", ambiguityProperties);
        ambiguityItemSchema.put("required", List.of("field", "code"));

        Map<String, Object> properties = new HashMap<>();
        properties.put("store_name", Map.of("type", "STRING"));
        properties.put("category_id", categorySchema);
        properties.put("category_candidates", categoryCandidatesSchema);
        properties.put(
                "amount",
                Map.of(
                        "type",
                        "NUMBER",
                        "description",
                        "Exact total payment amount as a positive decimal number"));
        properties.put(
                "currency",
                Map.of("type", "STRING", "description", "Three-letter ISO 4217 currency code"));
        properties.put(
                "occurred_at", Map.of("type", "STRING", "description", "ISO-8601 local date-time"));
        properties.put("subtotal", moneySchema("Receipt subtotal before adjustments"));
        properties.put("tax", moneySchema("Receipt tax"));
        properties.put("discount", moneySchema("Receipt discount as a positive value"));
        properties.put("tip", moneySchema("Receipt tip"));
        properties.put(
                "payment_method",
                Map.of("type", "STRING", "enum", PAYMENT_METHODS.stream().sorted().toList()));
        properties.put("ambiguities", Map.of("type", "ARRAY", "items", ambiguityItemSchema));

        Map<String, Object> responseSchema = new HashMap<>();
        responseSchema.put("type", "OBJECT");
        responseSchema.put("properties", properties);
        responseSchema.put(
                "required",
                List.of(
                        "store_name",
                        "category_id",
                        "category_candidates",
                        "amount",
                        "currency",
                        "payment_method",
                        "ambiguities"));

        return responseSchema;
    }

    private Map<String, Object> moneySchema(String description) {
        return Map.of("type", "NUMBER", "description", description);
    }

    ReceiptResponse parseGeminiResponse(
            String responseRaw, List<Category> validCategories, Long userId, String baseCurrency)
            throws IOException {
        JsonNode rootNode = jsonMapper.readTree(responseRaw);
        JsonNode candidate = rootNode.path("candidates").get(0);

        if (candidate == null || candidate.path("content").isMissingNode()) {
            throw new IllegalStateException("Did not get response from AI model.");
        }

        JsonNode part = candidate.path("content").path("parts").get(0);
        if (part == null || part.path("text").isMissingNode()) {
            throw new IllegalStateException("AI model response did not contain extracted data.");
        }

        return parseExtraction(
                part.path("text").asString(),
                validCategories,
                userId,
                baseCurrency,
                null,
                null,
                null,
                null);
    }

    private ReceiptResponse parseExtraction(
            String extractedJson,
            List<Category> validCategories,
            Long userId,
            String baseCurrency,
            AiRequestContext context,
            String promptVersion,
            AiExtractionResult extractionResult,
            UUID requestId)
            throws IOException {
        ReceiptExtraction extraction = jsonMapper.readValue(extractedJson, ReceiptExtraction.class);
        validateAmount(extraction.amount());

        List<AiFieldAmbiguity> ambiguities = normalizeAmbiguities(extraction.ambiguities());
        String storeName = normalizeStoreName(extraction.storeName(), ambiguities);
        String currency = normalizeCurrency(extraction.currency(), baseCurrency, ambiguities);
        LocalDateTime occurredAt = parseOccurredAt(extraction.occurredAt(), ambiguities, context);
        BigDecimal subtotal = validateOptionalMoney("subtotal", extraction.subtotal(), ambiguities);
        BigDecimal tax = validateOptionalMoney("tax", extraction.tax(), ambiguities);
        BigDecimal discount = validateOptionalMoney("discount", extraction.discount(), ambiguities);
        BigDecimal tip = validateOptionalMoney("tip", extraction.tip(), ambiguities);
        validateReceiptArithmetic(extraction.amount(), subtotal, tax, discount, tip, ambiguities);
        String paymentMethod = normalizePaymentMethod(extraction.paymentMethod(), ambiguities);

        Category category = findCategory(extraction.categoryId(), validCategories);
        boolean ruleApplied = false;
        if (context != null) {
            var matchingRule =
                    adaptationService.findMatchingRule(
                            userId, storeName, "EXPENSE", validCategories);
            if (matchingRule.isPresent()) {
                category = matchingRule.get();
                ruleApplied = true;
                ambiguities.removeIf(ambiguity -> "category_id".equals(ambiguity.field()));
            }
        }
        List<AiCategoryCandidate> categoryCandidates =
                buildCategoryCandidates(category, extraction.categoryCandidates(), validCategories);
        if (category == null) {
            addAmbiguity(ambiguities, "category_id", "UNRESOLVED");
        }

        List<AiDuplicateCandidate> duplicateCandidates =
                duplicateDetector.findCandidates(
                        userId, extraction.amount(), "EXPENSE", occurredAt, storeName);
        if (!duplicateCandidates.isEmpty()) {
            addAmbiguity(ambiguities, "transaction", "POSSIBLE_DUPLICATE");
        }

        boolean requiresClarification = !ambiguities.isEmpty();
        AiConfidenceResponse confidence =
                context == null
                        ? null
                        : confidenceScorer.score(
                                ambiguities, !duplicateCandidates.isEmpty(), ruleApplied);
        var extractionId =
                context == null
                        ? null
                        : adaptationService.recordExtraction(
                                new AiExtractionRecordRequest(
                                        userId,
                                        "RECEIPT",
                                        storeName,
                                        category != null ? category.getId() : null,
                                        "EXPENSE",
                                        extraction.amount(),
                                        currency,
                                        promptVersion,
                                        extractionResult.modelVersion(),
                                        extractionResult.provider(),
                                        confidence,
                                        ruleApplied,
                                        requiresClarification,
                                        context.localeTag(),
                                        context.timeZone(),
                                        occurredAt,
                                        null,
                                        null,
                                        List.copyOf(ambiguities)));

        return new ReceiptResponse(
                storeName,
                category != null ? category.getId() : null,
                category != null ? category.getName() : "Unknown",
                extraction.amount(),
                currency,
                occurredAt,
                subtotal,
                tax,
                discount,
                tip,
                paymentMethod,
                categoryCandidates,
                requiresClarification,
                List.copyOf(ambiguities),
                duplicateCandidates,
                extractionId,
                promptVersion,
                extractionResult != null ? extractionResult.modelVersion() : null,
                extractionResult != null ? extractionResult.provider() : null,
                context != null ? context.localeTag() : null,
                context != null ? context.timeZone() : null,
                ruleApplied,
                confidence,
                requestId);
    }

    private String formatCategories(List<Category> categories) {
        return categories.stream()
                .limit(MAX_PROMPT_CONTEXT_ENTRIES)
                .map(category -> category.getId() + " = " + category.getName())
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
    }

    private List<AiCategoryCandidate> buildCategoryCandidates(
            Category primary, List<String> candidateIds, List<Category> validCategories) {
        LinkedHashSet<Long> rankedIds = new LinkedHashSet<>();
        if (primary != null) {
            rankedIds.add(primary.getId());
        }
        if (candidateIds != null) {
            candidateIds.stream()
                    .map(candidateId -> findCategory(candidateId, validCategories))
                    .filter(category -> category != null)
                    .map(Category::getId)
                    .distinct()
                    .limit(3)
                    .forEach(rankedIds::add);
        }

        List<AiCategoryCandidate> candidates = new ArrayList<>();
        int rank = 1;
        for (Long categoryId : rankedIds.stream().limit(3).toList()) {
            Category category =
                    validCategories.stream()
                            .filter(candidate -> candidate.getId().equals(categoryId))
                            .findFirst()
                            .orElseThrow();
            candidates.add(new AiCategoryCandidate(category.getId(), category.getName(), rank++));
        }
        return List.copyOf(candidates);
    }

    private Category findCategory(String categoryId, List<Category> validCategories) {
        if (categoryId == null || UNKNOWN_CATEGORY_ID.equalsIgnoreCase(categoryId)) {
            return null;
        }
        return validCategories.stream()
                .filter(category -> category.getId().toString().equals(categoryId))
                .findFirst()
                .orElse(null);
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalStateException("AI model did not extract a valid positive amount.");
        }
        BigDecimal normalized = amount.stripTrailingZeros();
        if (Math.max(normalized.scale(), 0) > 4 || normalized.precision() > 19) {
            throw new IllegalStateException(
                    "AI model extracted an amount with unsupported precision or scale.");
        }
    }

    private String normalizeStoreName(String storeName, List<AiFieldAmbiguity> ambiguities) {
        if (storeName == null || storeName.isBlank()) {
            addAmbiguity(ambiguities, "store_name", "MISSING");
            return "Unknown merchant";
        }
        return storeName.trim();
    }

    private String normalizeCurrency(
            String extractedCurrency, String baseCurrency, List<AiFieldAmbiguity> ambiguities) {
        if (extractedCurrency == null || extractedCurrency.isBlank()) {
            addAmbiguity(ambiguities, "currency", "MISSING");
            return baseCurrency;
        }
        String currency = extractedCurrency.trim().toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) {
            addAmbiguity(ambiguities, "currency", "INVALID");
            return baseCurrency;
        }
        if (!currency.equals(baseCurrency)) {
            addAmbiguity(ambiguities, "currency", "BASE_CURRENCY_MISMATCH");
        }
        return currency;
    }

    private LocalDateTime parseOccurredAt(
            String rawOccurredAt,
            List<AiFieldAmbiguity> ambiguities,
            AiRequestContext requestContext) {
        if (rawOccurredAt == null || rawOccurredAt.isBlank()) {
            addAmbiguity(ambiguities, "occurred_at", "MISSING");
            return null;
        }
        try {
            LocalDateTime occurredAt;
            try {
                occurredAt = LocalDateTime.parse(rawOccurredAt.trim());
            } catch (DateTimeParseException exception) {
                occurredAt = LocalDate.parse(rawOccurredAt.trim()).atStartOfDay();
            }
            LocalDateTime now =
                    requestContext != null ? requestContext.now(clock) : LocalDateTime.now(clock);
            if (occurredAt.isAfter(now.plusMinutes(5)) || occurredAt.isBefore(now.minusYears(20))) {
                addAmbiguity(ambiguities, "occurred_at", "OUT_OF_RANGE");
                return null;
            }
            return occurredAt;
        } catch (DateTimeParseException exception) {
            addAmbiguity(ambiguities, "occurred_at", "INVALID");
            return null;
        }
    }

    private BigDecimal validateOptionalMoney(
            String field, BigDecimal value, List<AiFieldAmbiguity> ambiguities) {
        if (value == null) {
            return null;
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (value.signum() < 0
                || Math.max(normalized.scale(), 0) > 4
                || normalized.precision() > 19) {
            addAmbiguity(ambiguities, field, "INVALID");
            return null;
        }
        return value;
    }

    private void validateReceiptArithmetic(
            BigDecimal total,
            BigDecimal subtotal,
            BigDecimal tax,
            BigDecimal discount,
            BigDecimal tip,
            List<AiFieldAmbiguity> ambiguities) {
        if (subtotal == null) {
            return;
        }
        BigDecimal expected = subtotal;
        if (tax != null) {
            expected = expected.add(tax);
        }
        if (tip != null) {
            expected = expected.add(tip);
        }
        if (discount != null) {
            expected = expected.subtract(discount);
        }
        if (expected.subtract(total).abs().compareTo(ARITHMETIC_TOLERANCE) > 0) {
            addAmbiguity(ambiguities, "amount", "ARITHMETIC_MISMATCH");
        }
    }

    private String normalizePaymentMethod(
            String paymentMethod, List<AiFieldAmbiguity> ambiguities) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            addAmbiguity(ambiguities, "payment_method", "MISSING");
            return "UNKNOWN";
        }
        String normalized = paymentMethod.trim().toUpperCase(Locale.ROOT);
        if (!PAYMENT_METHODS.contains(normalized)) {
            addAmbiguity(ambiguities, "payment_method", "INVALID");
            return "UNKNOWN";
        }
        if ("UNKNOWN".equals(normalized)) {
            addAmbiguity(ambiguities, "payment_method", "UNCLEAR");
        }
        return normalized;
    }

    private List<AiFieldAmbiguity> normalizeAmbiguities(List<ModelAmbiguity> ambiguities) {
        if (ambiguities == null) {
            return new ArrayList<>();
        }
        List<AiFieldAmbiguity> result = new ArrayList<>();
        ambiguities.stream()
                .filter(ambiguity -> ambiguity.field() != null && ambiguity.code() != null)
                .map(
                        ambiguity ->
                                new AiFieldAmbiguity(
                                        ambiguity.field().trim().toLowerCase(Locale.ROOT),
                                        ambiguity.code().trim().toUpperCase(Locale.ROOT)))
                .forEach(ambiguity -> addAmbiguity(result, ambiguity.field(), ambiguity.code()));
        return result;
    }

    private void addAmbiguity(List<AiFieldAmbiguity> ambiguities, String field, String code) {
        AiFieldAmbiguity ambiguity = new AiFieldAmbiguity(field, code);
        if (!ambiguities.contains(ambiguity)) {
            ambiguities.add(ambiguity);
        }
    }

    private record ReceiptExtraction(
            @JsonProperty("store_name") String storeName,
            @JsonProperty("category_id") String categoryId,
            @JsonProperty("category_candidates") List<String> categoryCandidates,
            BigDecimal amount,
            String currency,
            @JsonProperty("occurred_at") String occurredAt,
            BigDecimal subtotal,
            BigDecimal tax,
            BigDecimal discount,
            BigDecimal tip,
            @JsonProperty("payment_method") String paymentMethod,
            List<ModelAmbiguity> ambiguities) {}

    private record ModelAmbiguity(String field, String code) {}
}
