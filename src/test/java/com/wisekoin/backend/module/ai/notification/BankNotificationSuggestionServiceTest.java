package com.wisekoin.backend.module.ai.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wisekoin.backend.module.ai.AiDuplicateDetector;
import com.wisekoin.backend.module.ai.adaptive.AiAdaptationService;
import com.wisekoin.backend.module.ai.adaptive.AiExtraction;
import com.wisekoin.backend.module.ai.adaptive.AiExtractionRecordRequest;
import com.wisekoin.backend.module.ai.adaptive.AiExtractionRepository;
import com.wisekoin.backend.module.ai.notification.dto.BankNotificationSuggestionRequest;
import com.wisekoin.backend.module.ai.suggestion.AiSuggestionService;
import com.wisekoin.backend.module.ai.suggestion.dto.AiSuggestionResponse;
import com.wisekoin.backend.module.common.exception.BadRequestException;
import com.wisekoin.backend.module.common.exception.IdempotencyConflictException;
import com.wisekoin.backend.module.transaction.Transaction;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BankNotificationSuggestionServiceTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AiExtractionRepository extractionRepository = mock(AiExtractionRepository.class);
    private final AiAdaptationService adaptationService = mock(AiAdaptationService.class);
    private final AiSuggestionService suggestionService = mock(AiSuggestionService.class);
    private final AiDuplicateDetector duplicateDetector = mock(AiDuplicateDetector.class);
    private final NotificationPilotMetricService metricService =
            mock(NotificationPilotMetricService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-25T03:00:00Z"), ZoneOffset.UTC);
    private final BankNotificationSuggestionService service =
            new BankNotificationSuggestionService(
                    userRepository,
                    extractionRepository,
                    adaptationService,
                    suggestionService,
                    duplicateDetector,
                    metricService,
                    clock);

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(42L);
        user.setBaseCurrency("VND");
        when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
    }

    @Test
    void createsDeidentifiedPendingSuggestionFromAllowlistedParser() {
        UUID extractionId = UUID.randomUUID();
        AiExtraction extraction = new AiExtraction();
        extraction.setId(extractionId);
        when(extractionRepository.findByUserIdAndSourceAndSourceReferenceHash(
                        42L, "BANK_NOTIFICATION", hash()))
                .thenReturn(Optional.empty());
        when(adaptationService.recordExtraction(any())).thenReturn(extractionId);
        when(extractionRepository.findById(extractionId)).thenReturn(Optional.of(extraction));
        AiSuggestionResponse response = mock(AiSuggestionResponse.class);
        when(suggestionService.get(42L, extractionId)).thenReturn(response);

        assertEquals(response, service.create(42L, request("VND", "vcb-v1")));

        ArgumentCaptor<AiExtractionRecordRequest> captor =
                ArgumentCaptor.forClass(AiExtractionRecordRequest.class);
        verify(adaptationService).recordExtraction(captor.capture());
        assertEquals("BANK_NOTIFICATION", captor.getValue().source());
        assertEquals("VCB", captor.getValue().merchant());
        assertEquals(
                List.of("category_id:UNRESOLVED"),
                captor.getValue().ambiguities().stream()
                        .map(value -> value.field() + ":" + value.code())
                        .toList());
        assertEquals(hash(), extraction.getSourceReferenceHash());
        assertEquals("vcb-v1", extraction.getParserVersion());
        verify(metricService).recordUpload(42L, BankNotificationSource.VCB, "vcb-v1", false, 0);
    }

    @Test
    void rejectsCurrencyMismatchAndUnknownParserVersion() {
        assertThrows(
                BadRequestException.class, () -> service.create(42L, request("USD", "vcb-v1")));
        assertThrows(
                BadRequestException.class, () -> service.create(42L, request("VND", "vcb-v2")));
    }

    @Test
    void rejectsReusedNotificationReferenceWithDifferentData() {
        AiExtraction extraction = new AiExtraction();
        extraction.setId(UUID.randomUUID());
        extraction.setMerchantLabel("VCB");
        extraction.setParserVersion("vcb-v1");
        extraction.setTransactionType("EXPENSE");
        extraction.setAmount(new BigDecimal("999"));
        extraction.setCurrency("VND");
        extraction.setOccurredAt(LocalDateTime.of(2026, 10, 25, 9, 0));
        when(extractionRepository.findByUserIdAndSourceAndSourceReferenceHash(
                        42L, "BANK_NOTIFICATION", hash()))
                .thenReturn(Optional.of(extraction));

        assertThrows(
                IdempotencyConflictException.class,
                () -> service.create(42L, request("VND", "vcb-v1")));
    }

    private BankNotificationSuggestionRequest request(String currency, String parserVersion) {
        return new BankNotificationSuggestionRequest(
                "VCB",
                hash(),
                parserVersion,
                new BigDecimal("125000"),
                currency,
                Transaction.TransactionType.EXPENSE,
                LocalDateTime.of(2026, 10, 25, 9, 0),
                "vi-VN",
                "Asia/Ho_Chi_Minh");
    }

    private String hash() {
        return "a".repeat(64);
    }
}
