package com.wisekoin.backend.module.voice;

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
import com.wisekoin.backend.module.ai.prompt.AiPromptService;
import com.wisekoin.backend.module.ai.prompt.VersionedPrompt;
import com.wisekoin.backend.module.ai.provider.AiExtractionClient;
import com.wisekoin.backend.module.ai.provider.AiExtractionRequest;
import com.wisekoin.backend.module.ai.provider.AiExtractionResult;
import com.wisekoin.backend.module.category.Category;
import com.wisekoin.backend.module.category.CategoryService;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserService;
import com.wisekoin.backend.module.voice.dto.VoiceResponse;
import com.wisekoin.backend.module.voice.dto.VoiceTransactionResponse;
import com.wisekoin.backend.module.wallet.Wallet;
import com.wisekoin.backend.module.wallet.WalletRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class VoiceService {
    static final String UNKNOWN_CATEGORY_ID = "UNKNOWN";
    static final String UNKNOWN_TYPE = "UNKNOWN";
    private static final int MAX_TRANSACTIONS_PER_RECORDING = 10;
    private static final int MAX_PROMPT_CONTEXT_ENTRIES = 200;

    private final AiExtractionClient extractionClient;
    private final AiPromptService promptService;
    private final AiRequestContextResolver contextResolver;
    private final AiAdaptationService adaptationService;
    private final AiConfidenceScorer confidenceScorer;
    private final JsonMapper jsonMapper;
    private final CategoryService categoryService;
    private final UserService userService;
    private final AiDuplicateDetector duplicateDetector;
    private final WalletRepository walletRepository;
    private final Clock clock;

    public VoiceResponse extractVoice(MultipartFile file, Long userId) throws IOException {
        return extractVoice(file, userId, null, null);
    }

    public VoiceResponse extractVoice(
            MultipartFile file, Long userId, String locale, String timeZone) throws IOException {
        String mimeType = validateAndNormalizeMimeType(file);
        String base64Audio = encodeAudioToBase64(file);
        String currency = getBaseCurrency(userId);
        List<Category> categories = getTransactionCategories(userId);
        List<Wallet> wallets = walletRepository.findAllByUserId(userId);
        AiRequestContext context = contextResolver.resolve(locale, timeZone);
        String rules =
                String.join(
                        "; ",
                        adaptationService.formatRules(userId, "EXPENSE", categories),
                        adaptationService.formatRules(userId, "INCOME", categories));
        VersionedPrompt prompt =
                promptService.voice(
                        Map.of(
                                "CURRENT_DATETIME", context.now(clock).toString(),
                                "LOCALE", context.localeTag(),
                                "TIME_ZONE", context.timeZone(),
                                "BASE_CURRENCY", currency,
                                "EXPENSE_CATEGORIES",
                                        formatCategories(categories, Category.CategoryType.EXPENSE),
                                "INCOME_CATEGORIES",
                                        formatCategories(categories, Category.CategoryType.INCOME),
                                "WALLETS", formatWallets(wallets),
                                "MERCHANT_RULES", rules));
        AiExtractionResult result =
                extractionClient.extract(
                        new AiExtractionRequest(
                                prompt.content(),
                                mimeType,
                                base64Audio,
                                buildResponseSchema(categories, wallets, currency),
                                0.0));

        return parseExtraction(
                result.content(),
                categories,
                currency,
                userId,
                new AdaptiveVoiceContext(context, prompt.version(), result, wallets));
    }

    List<Category> getTransactionCategories(Long userId) {
        List<Category> categories = new ArrayList<>();
        categories.addAll(categoryService.getCategoryByType(Category.CategoryType.EXPENSE, userId));
        categories.addAll(categoryService.getCategoryByType(Category.CategoryType.INCOME, userId));
        return categories;
    }

    private String getBaseCurrency(Long userId) {
        User user = userService.getUserById(userId);
        if (user.getBaseCurrency() == null || user.getBaseCurrency().isBlank()) {
            return "VND";
        }
        return user.getBaseCurrency().trim().toUpperCase(Locale.ROOT);
    }

    public String validateAndNormalizeMimeType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty.");
        }

        String rawMimeType = file.getContentType();
        String filename =
                file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";

        if ("audio/mp4".equalsIgnoreCase(rawMimeType)
                || "audio/m4a".equalsIgnoreCase(rawMimeType)
                || "audio/x-m4a".equalsIgnoreCase(rawMimeType)
                || filename.endsWith(".m4a")
                || filename.endsWith(".mp4")) {
            return "audio/mp4";
        } else if ("audio/mpeg".equalsIgnoreCase(rawMimeType)
                || "audio/mp3".equalsIgnoreCase(rawMimeType)
                || filename.endsWith(".mp3")) {
            return "audio/mp3";
        } else if ("audio/wav".equalsIgnoreCase(rawMimeType)
                || "audio/x-wav".equalsIgnoreCase(rawMimeType)
                || filename.endsWith(".wav")) {
            return "audio/wav";
        } else if ("audio/aac".equalsIgnoreCase(rawMimeType) || filename.endsWith(".aac")) {
            return "audio/aac";
        } else if ("audio/ogg".equalsIgnoreCase(rawMimeType) || filename.endsWith(".ogg")) {
            return "audio/ogg";
        }

        throw new IllegalArgumentException(
                "Do not support audio format: "
                        + rawMimeType
                        + ". Only accept m4a, mp3, wav, aac, and ogg.");
    }

    private String encodeAudioToBase64(MultipartFile file) throws IOException {
        return Base64.getEncoder().encodeToString(file.getBytes());
    }

    Map<String, Object> buildResponseSchema(
            List<Category> categories, List<Wallet> wallets, String currency) {
        List<String> categoryIds =
                new ArrayList<>(
                        categories.stream().map(category -> category.getId().toString()).toList());
        categoryIds.add(UNKNOWN_CATEGORY_ID);

        Map<String, Object> categorySchema = new HashMap<>();
        categorySchema.put("type", "STRING");
        categorySchema.put("enum", categoryIds);
        categorySchema.put(
                "description",
                "Stable ID of a category whose category type matches the transaction type, or UNKNOWN.");

        Map<String, Object> categoryCandidatesSchema = new HashMap<>();
        categoryCandidatesSchema.put("type", "ARRAY");
        categoryCandidatesSchema.put("items", categorySchema);
        categoryCandidatesSchema.put("maxItems", 3);

        Map<String, Object> ambiguityProperties = new HashMap<>();
        ambiguityProperties.put(
                "field",
                Map.of(
                        "type",
                        "STRING",
                        "enum",
                        List.of(
                                "amount",
                                "type",
                                "category_id",
                                "note",
                                "occurred_at",
                                "counterparty",
                                "source_wallet_id",
                                "destination_wallet_id",
                                "source_wallet_hint",
                                "destination_wallet_hint")));
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

        Map<String, Object> transactionProperties = new HashMap<>();
        transactionProperties.put(
                "amount",
                Map.of(
                        "type",
                        "NUMBER",
                        "description",
                        "Positive transaction amount in " + currency + " without symbols"));
        transactionProperties.put(
                "type",
                Map.of(
                        "type",
                        "STRING",
                        "enum",
                        List.of("EXPENSE", "INCOME", "TRANSFER", UNKNOWN_TYPE),
                        "description",
                        "Transaction type, or UNKNOWN when ambiguous"));
        transactionProperties.put("category_id", categorySchema);
        transactionProperties.put("category_candidates", categoryCandidatesSchema);
        transactionProperties.put(
                "note",
                Map.of(
                        "type",
                        "STRING",
                        "description",
                        "Short note or description of the transaction"));
        transactionProperties.put(
                "counterparty",
                Map.of("type", "STRING", "description", "Merchant or counterparty, or empty"));
        transactionProperties.put(
                "occurred_at", Map.of("type", "STRING", "description", "ISO-8601 local date-time"));
        transactionProperties.put(
                "source_wallet_hint",
                Map.of("type", "STRING", "description", "Spoken source wallet hint or empty"));
        transactionProperties.put(
                "destination_wallet_hint",
                Map.of("type", "STRING", "description", "Spoken destination wallet hint or empty"));
        List<String> walletIds =
                new ArrayList<>(wallets.stream().map(wallet -> wallet.getId().toString()).toList());
        walletIds.add(UNKNOWN_CATEGORY_ID);
        Map<String, Object> walletSchema =
                Map.of(
                        "type",
                        "STRING",
                        "enum",
                        walletIds,
                        "description",
                        "Stable owned wallet ID, or UNKNOWN");
        transactionProperties.put("source_wallet_id", walletSchema);
        transactionProperties.put("destination_wallet_id", walletSchema);
        transactionProperties.put(
                "ambiguities", Map.of("type", "ARRAY", "items", ambiguityItemSchema));

        Map<String, Object> transactionSchema = new HashMap<>();
        transactionSchema.put("type", "OBJECT");
        transactionSchema.put("properties", transactionProperties);
        transactionSchema.put(
                "required",
                List.of(
                        "amount",
                        "type",
                        "category_id",
                        "category_candidates",
                        "note",
                        "counterparty",
                        "source_wallet_id",
                        "destination_wallet_id",
                        "source_wallet_hint",
                        "destination_wallet_hint",
                        "ambiguities"));

        Map<String, Object> transactionsSchema = new HashMap<>();
        transactionsSchema.put("type", "ARRAY");
        transactionsSchema.put("items", transactionSchema);
        transactionsSchema.put("minItems", 1);
        transactionsSchema.put("maxItems", MAX_TRANSACTIONS_PER_RECORDING);

        Map<String, Object> properties = new HashMap<>();
        properties.put(
                "transcript",
                Map.of(
                        "type",
                        "STRING",
                        "description",
                        "The user's complete spoken words transcribed in the original language"));
        properties.put("transactions", transactionsSchema);

        Map<String, Object> responseSchema = new HashMap<>();
        responseSchema.put("type", "OBJECT");
        responseSchema.put("properties", properties);
        responseSchema.put("required", List.of("transcript", "transactions"));

        return responseSchema;
    }

    private String formatCategories(List<Category> categories, Category.CategoryType categoryType) {
        return categories.stream()
                .filter(category -> category.getType() == categoryType)
                .limit(MAX_PROMPT_CONTEXT_ENTRIES)
                .map(category -> category.getId() + " = " + category.getName())
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
    }

    private String formatWallets(List<Wallet> wallets) {
        return wallets.stream()
                .limit(MAX_PROMPT_CONTEXT_ENTRIES)
                .map(wallet -> wallet.getId() + " = " + wallet.getName())
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
    }

    public VoiceResponse parseGeminiResponse(
            String responseRaw, List<Category> validCategories, String currency)
            throws IOException {
        return parseGeminiResponse(responseRaw, validCategories, currency, null);
    }

    VoiceResponse parseGeminiResponse(
            String responseRaw, List<Category> validCategories, String currency, Long userId)
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
                part.path("text").asString(), validCategories, currency, userId, null);
    }

    private VoiceResponse parseExtraction(
            String extractedJsonText,
            List<Category> validCategories,
            String currency,
            Long userId,
            AdaptiveVoiceContext adaptiveContext)
            throws IOException {
        extractedJsonText = stripMarkdownFence(extractedJsonText);
        JsonNode extractionNode = jsonMapper.readTree(extractedJsonText);
        VoiceBatchExtraction batch = readBatchExtraction(extractionNode);
        if (batch.transactions() == null || batch.transactions().isEmpty()) {
            throw new IllegalStateException("AI model did not extract any transactions.");
        }
        if (batch.transactions().size() > MAX_TRANSACTIONS_PER_RECORDING) {
            throw new IllegalStateException("AI model extracted too many transactions.");
        }

        List<VoiceTransactionResponse> transactions =
                batch.transactions().stream()
                        .map(
                                extraction ->
                                        buildTransactionResponse(
                                                extraction,
                                                validCategories,
                                                currency,
                                                userId,
                                                adaptiveContext))
                        .toList();
        VoiceTransactionResponse first = transactions.getFirst();
        return new VoiceResponse(
                first.amount(),
                currency,
                first.type(),
                first.categoryId(),
                first.category(),
                first.note(),
                batch.transcript() != null ? batch.transcript() : "",
                first.occurredAt(),
                first.sourceWalletHint(),
                first.destinationWalletHint(),
                transactions.stream().anyMatch(VoiceTransactionResponse::requiresClarification),
                transactions,
                first.extractionId(),
                first.counterparty(),
                first.sourceWalletId(),
                first.destinationWalletId());
    }

    private VoiceBatchExtraction readBatchExtraction(JsonNode extractionNode) throws IOException {
        if (extractionNode.has("transactions")) {
            return jsonMapper.treeToValue(extractionNode, VoiceBatchExtraction.class);
        }
        VoiceExtraction legacy = jsonMapper.treeToValue(extractionNode, VoiceExtraction.class);
        return new VoiceBatchExtraction(legacy.transcript(), List.of(legacy));
    }

    private VoiceTransactionResponse buildTransactionResponse(
            VoiceExtraction extraction,
            List<Category> validCategories,
            String currency,
            Long userId,
            AdaptiveVoiceContext adaptiveContext) {
        validateAmount(extraction.amount());
        List<AiFieldAmbiguity> ambiguities = normalizeAmbiguities(extraction.ambiguities());
        String type = normalizeType(extraction.type());
        if (UNKNOWN_TYPE.equals(type)) {
            addAmbiguity(ambiguities, "type", "UNRESOLVED");
        }

        String note = extraction.note() != null ? extraction.note().trim() : "";
        String counterparty =
                extraction.counterparty() != null ? extraction.counterparty().trim() : "";
        String ruleMerchant = counterparty.isBlank() ? note : counterparty;
        Category category = findMatchingCategory(extraction.categoryId(), type, validCategories);
        boolean ruleApplied = false;
        if (adaptiveContext != null) {
            var matchingRule =
                    adaptationService.findMatchingRule(userId, ruleMerchant, type, validCategories);
            if (matchingRule.isPresent()) {
                category = matchingRule.get();
                ruleApplied = true;
                ambiguities.removeIf(ambiguity -> "category_id".equals(ambiguity.field()));
            }
        }
        if (("EXPENSE".equals(type) || "INCOME".equals(type)) && category == null) {
            addAmbiguity(ambiguities, "category_id", "UNRESOLVED");
        }
        List<AiCategoryCandidate> categoryCandidates =
                buildCategoryCandidates(
                        category, extraction.categoryCandidates(), type, validCategories);

        LocalDateTime occurredAt =
                parseOccurredAt(
                        extraction.occurredAt(),
                        ambiguities,
                        adaptiveContext != null ? adaptiveContext.context() : null);
        String sourceWalletHint = normalizeHint(extraction.sourceWalletHint());
        String destinationWalletHint = normalizeHint(extraction.destinationWalletHint());
        Long sourceWalletId =
                findOwnedWalletId(
                        extraction.sourceWalletId(),
                        adaptiveContext != null ? adaptiveContext.wallets() : List.of());
        Long destinationWalletId =
                findOwnedWalletId(
                        extraction.destinationWalletId(),
                        adaptiveContext != null ? adaptiveContext.wallets() : List.of());
        if ("TRANSFER".equals(type)) {
            if (sourceWalletId == null) {
                addAmbiguity(ambiguities, "source_wallet_id", "UNRESOLVED");
            }
            if (destinationWalletId == null) {
                addAmbiguity(ambiguities, "destination_wallet_id", "UNRESOLVED");
            }
            if (sourceWalletId != null && sourceWalletId.equals(destinationWalletId)) {
                addAmbiguity(ambiguities, "wallets", "CONFLICTING");
            }
        }

        List<AiDuplicateCandidate> duplicateCandidates =
                userId == null
                        ? List.of()
                        : duplicateDetector.findCandidates(
                                userId, extraction.amount(), type, occurredAt, note);
        if (!duplicateCandidates.isEmpty()) {
            addAmbiguity(ambiguities, "transaction", "POSSIBLE_DUPLICATE");
        }

        boolean requiresClarification = !ambiguities.isEmpty();
        AiConfidenceResponse confidence =
                adaptiveContext == null
                        ? null
                        : confidenceScorer.score(
                                ambiguities, !duplicateCandidates.isEmpty(), ruleApplied);
        var extractionId =
                adaptiveContext == null
                        ? null
                        : adaptationService.recordExtraction(
                                new AiExtractionRecordRequest(
                                        userId,
                                        "VOICE",
                                        ruleMerchant,
                                        category != null ? category.getId() : null,
                                        type,
                                        extraction.amount(),
                                        currency,
                                        adaptiveContext.promptVersion(),
                                        adaptiveContext.result().modelVersion(),
                                        adaptiveContext.result().provider(),
                                        confidence,
                                        ruleApplied,
                                        requiresClarification,
                                        adaptiveContext.context().localeTag(),
                                        adaptiveContext.context().timeZone()));

        return new VoiceTransactionResponse(
                extraction.amount(),
                currency,
                type,
                category != null ? category.getId() : null,
                category != null ? category.getName() : "Unknown",
                note,
                occurredAt,
                sourceWalletHint,
                destinationWalletHint,
                categoryCandidates,
                requiresClarification,
                List.copyOf(ambiguities),
                duplicateCandidates,
                extractionId,
                counterparty,
                sourceWalletId,
                destinationWalletId,
                adaptiveContext != null ? adaptiveContext.promptVersion() : null,
                adaptiveContext != null ? adaptiveContext.result().modelVersion() : null,
                adaptiveContext != null ? adaptiveContext.result().provider() : null,
                adaptiveContext != null ? adaptiveContext.context().localeTag() : null,
                adaptiveContext != null ? adaptiveContext.context().timeZone() : null,
                ruleApplied,
                confidence);
    }

    private Long findOwnedWalletId(String walletId, List<Wallet> wallets) {
        if (walletId == null || UNKNOWN_CATEGORY_ID.equalsIgnoreCase(walletId)) {
            return null;
        }
        return wallets.stream()
                .filter(wallet -> wallet.getId().toString().equals(walletId))
                .map(Wallet::getId)
                .findFirst()
                .orElse(null);
    }

    private String stripMarkdownFence(String extractedJsonText) {
        if (extractedJsonText == null) {
            throw new IllegalStateException("AI model response did not contain extracted data.");
        }
        String result = extractedJsonText.trim();
        if (result.startsWith("```json")) {
            result = result.substring(7);
        } else if (result.startsWith("```")) {
            result = result.substring(3);
        }
        if (result.endsWith("```")) {
            result = result.substring(0, result.length() - 3);
        }
        return result.trim();
    }

    private String normalizeType(String type) {
        if ("EXPENSE".equalsIgnoreCase(type)) {
            return "EXPENSE";
        }
        if ("INCOME".equalsIgnoreCase(type)) {
            return "INCOME";
        }
        if ("TRANSFER".equalsIgnoreCase(type)) {
            return "TRANSFER";
        }
        return UNKNOWN_TYPE;
    }

    private List<AiCategoryCandidate> buildCategoryCandidates(
            Category primary,
            List<String> candidateIds,
            String transactionType,
            List<Category> validCategories) {
        Category.CategoryType expectedType = expectedCategoryType(transactionType);
        if (expectedType == null) {
            return List.of();
        }

        LinkedHashSet<Long> rankedIds = new LinkedHashSet<>();
        if (primary != null) {
            rankedIds.add(primary.getId());
        }
        if (candidateIds != null) {
            candidateIds.stream()
                    .map(
                            categoryId ->
                                    findMatchingCategory(
                                            categoryId, transactionType, validCategories))
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
                            .filter(candidate -> candidate.getType() == expectedType)
                            .findFirst()
                            .orElseThrow();
            candidates.add(new AiCategoryCandidate(category.getId(), category.getName(), rank++));
        }
        return List.copyOf(candidates);
    }

    private Category findMatchingCategory(
            String categoryId, String transactionType, List<Category> validCategories) {
        Category.CategoryType expectedType = expectedCategoryType(transactionType);
        if (expectedType == null
                || categoryId == null
                || UNKNOWN_CATEGORY_ID.equalsIgnoreCase(categoryId)) {
            return null;
        }
        return validCategories.stream()
                .filter(category -> category.getType() == expectedType)
                .filter(category -> category.getId().toString().equals(categoryId))
                .findFirst()
                .orElse(null);
    }

    private Category.CategoryType expectedCategoryType(String transactionType) {
        return switch (transactionType) {
            case "EXPENSE" -> Category.CategoryType.EXPENSE;
            case "INCOME" -> Category.CategoryType.INCOME;
            default -> null;
        };
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

    private String normalizeHint(String hint) {
        return hint == null ? "" : hint.trim();
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

    private record VoiceExtraction(
            BigDecimal amount,
            String type,
            @JsonProperty("category_id") String categoryId,
            @JsonProperty("category_candidates") List<String> categoryCandidates,
            String note,
            String counterparty,
            String transcript,
            @JsonProperty("occurred_at") String occurredAt,
            @JsonProperty("source_wallet_id") String sourceWalletId,
            @JsonProperty("destination_wallet_id") String destinationWalletId,
            @JsonProperty("source_wallet_hint") String sourceWalletHint,
            @JsonProperty("destination_wallet_hint") String destinationWalletHint,
            List<ModelAmbiguity> ambiguities) {}

    private record VoiceBatchExtraction(String transcript, List<VoiceExtraction> transactions) {}

    private record ModelAmbiguity(String field, String code) {}

    private record AdaptiveVoiceContext(
            AiRequestContext context,
            String promptVersion,
            AiExtractionResult result,
            List<Wallet> wallets) {}
}
