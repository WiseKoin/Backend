package com.wisekoin.backend.module.ai.notification;

import com.wisekoin.backend.module.ai.AiDuplicateDetector;
import com.wisekoin.backend.module.ai.adaptive.AiAdaptationService;
import com.wisekoin.backend.module.ai.adaptive.AiExtraction;
import com.wisekoin.backend.module.ai.adaptive.AiExtractionRecordRequest;
import com.wisekoin.backend.module.ai.adaptive.AiExtractionRepository;
import com.wisekoin.backend.module.ai.dto.AiConfidenceResponse;
import com.wisekoin.backend.module.ai.dto.AiFieldAmbiguity;
import com.wisekoin.backend.module.ai.notification.dto.BankNotificationSuggestionRequest;
import com.wisekoin.backend.module.ai.suggestion.AiSuggestionService;
import com.wisekoin.backend.module.ai.suggestion.dto.AiSuggestionResponse;
import com.wisekoin.backend.module.common.exception.BadRequestException;
import com.wisekoin.backend.module.common.exception.IdempotencyConflictException;
import com.wisekoin.backend.module.common.exception.ResourceNotFoundException;
import com.wisekoin.backend.module.transaction.Transaction;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BankNotificationSuggestionService {
    private static final int MAX_AGE_DAYS = 14;

    private final UserRepository userRepository;
    private final AiExtractionRepository extractionRepository;
    private final AiAdaptationService adaptationService;
    private final AiSuggestionService suggestionService;
    private final AiDuplicateDetector duplicateDetector;
    private final NotificationPilotMetricService metricService;
    private final Clock clock;

    @Transactional
    public AiSuggestionResponse create(Long userId, BankNotificationSuggestionRequest request) {
        User user =
                userRepository
                        .findByIdForUpdate(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        BankNotificationSource source = BankNotificationSource.from(request.source());
        source.validateParserVersion(request.parserVersion());
        validate(request, user);

        String referenceHash = request.sourceReferenceHash().toLowerCase(Locale.ROOT);
        var existing =
                extractionRepository.findByUserIdAndSourceAndSourceReferenceHash(
                        userId, "BANK_NOTIFICATION", referenceHash);
        if (existing.isPresent()) {
            validateReplay(existing.get(), request, source);
            metricService.recordUpload(userId, source, request.parserVersion(), true, 0);
            return suggestionService.get(userId, existing.get().getId());
        }

        var duplicates =
                duplicateDetector.findCandidates(
                        userId,
                        request.amount(),
                        request.type().name(),
                        request.occurredAt(),
                        source.displayName());
        List<AiFieldAmbiguity> ambiguities =
                new ArrayList<>(List.of(new AiFieldAmbiguity("category_id", "UNRESOLVED")));
        if (!duplicates.isEmpty()) {
            ambiguities.add(new AiFieldAmbiguity("transaction", "POSSIBLE_DUPLICATE"));
        }
        AiConfidenceResponse confidence =
                new AiConfidenceResponse(
                        new BigDecimal("0.6500"),
                        "MEDIUM",
                        List.of("LOCAL_ALLOWLISTED_PARSER", "CATEGORY_REQUIRES_REVIEW"),
                        "notification-pilot-v1",
                        false);
        UUID id =
                adaptationService.recordExtraction(
                        new AiExtractionRecordRequest(
                                userId,
                                "BANK_NOTIFICATION",
                                source.name(),
                                null,
                                request.type().name(),
                                request.amount(),
                                request.currency().toUpperCase(Locale.ROOT),
                                request.parserVersion(),
                                "android-local-parser",
                                "android",
                                confidence,
                                false,
                                true,
                                request.locale(),
                                request.timeZone(),
                                request.occurredAt(),
                                null,
                                null,
                                ambiguities));
        AiExtraction extraction = extractionRepository.findById(id).orElseThrow();
        extraction.setSourceReferenceHash(referenceHash);
        extraction.setParserVersion(request.parserVersion());
        metricService.recordUpload(
                userId, source, request.parserVersion(), false, duplicates.size());
        return suggestionService.get(userId, id);
    }

    private void validate(BankNotificationSuggestionRequest request, User user) {
        if (request.type() != Transaction.TransactionType.EXPENSE
                && request.type() != Transaction.TransactionType.INCOME) {
            throw new BadRequestException("Bank notification type must be EXPENSE or INCOME");
        }
        if (!request.currency().equalsIgnoreCase(user.getBaseCurrency())) {
            throw new BadRequestException(
                    "Notification currency must match the user's base currency");
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(request.timeZone());
        } catch (DateTimeException exception) {
            throw new BadRequestException("Invalid notification time zone");
        }
        LocalDateTime now = LocalDateTime.now(clock.withZone(zone));
        if (request.occurredAt().isAfter(now.plusMinutes(5))
                || request.occurredAt().isBefore(now.minusDays(MAX_AGE_DAYS))) {
            throw new BadRequestException("Notification occurrence time is out of range");
        }
    }

    private void validateReplay(
            AiExtraction extraction,
            BankNotificationSuggestionRequest request,
            BankNotificationSource source) {
        boolean sameRequest =
                source.name().equals(extraction.getMerchantLabel())
                        && request.parserVersion().equals(extraction.getParserVersion())
                        && request.type().name().equals(extraction.getTransactionType())
                        && request.amount().compareTo(extraction.getAmount()) == 0
                        && request.currency().equalsIgnoreCase(extraction.getCurrency())
                        && request.occurredAt().equals(extraction.getOccurredAt());
        if (!sameRequest) {
            throw new IdempotencyConflictException(
                    "Notification reference was already used with different suggestion data");
        }
    }
}
