package com.wisekoin.backend.module.ai.suggestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wisekoin.backend.module.ai.adaptive.AiExtraction;
import com.wisekoin.backend.module.ai.adaptive.AiExtractionRepository;
import com.wisekoin.backend.module.ai.adaptive.MerchantNormalizer;
import com.wisekoin.backend.module.ai.suggestion.dto.AiSuggestionClarificationRequest;
import com.wisekoin.backend.module.ai.suggestion.dto.AiSuggestionConfirmationRequest;
import com.wisekoin.backend.module.ai.suggestion.dto.AiSuggestionRejectionRequest;
import com.wisekoin.backend.module.category.Category;
import com.wisekoin.backend.module.category.CategoryRepository;
import com.wisekoin.backend.module.common.exception.BadRequestException;
import com.wisekoin.backend.module.common.exception.ResourceNotFoundException;
import com.wisekoin.backend.module.transaction.Transaction;
import com.wisekoin.backend.module.transaction.TransactionService;
import com.wisekoin.backend.module.transaction.dto.TransactionRequest;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserRepository;
import com.wisekoin.backend.module.wallet.Wallet;
import com.wisekoin.backend.module.wallet.WalletRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiSuggestionServiceTest {
    private final AiExtractionRepository extractionRepository = mock(AiExtractionRepository.class);
    private final AiSuggestionActionRepository actionRepository =
            mock(AiSuggestionActionRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final WalletRepository walletRepository = mock(WalletRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TransactionService transactionService = mock(TransactionService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-24T03:00:00Z"), ZoneOffset.UTC);
    private final AiSuggestionService service =
            new AiSuggestionService(
                    extractionRepository,
                    actionRepository,
                    categoryRepository,
                    walletRepository,
                    userRepository,
                    transactionService,
                    new MerchantNormalizer(),
                    clock);

    private UUID suggestionId;
    private User user;
    private AiExtraction suggestion;

    @BeforeEach
    void setUp() {
        suggestionId = UUID.randomUUID();
        user = new User();
        user.setId(42L);
        user.setBaseCurrency("VND");
        suggestion = suggestion();
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(userRepository.getReferenceById(42L)).thenReturn(user);
        when(actionRepository.save(any(AiSuggestionAction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void clarificationClearsResolvedAmbiguitiesAndKeepsSuggestionPending() {
        Wallet wallet = new Wallet();
        wallet.setId(5L);
        wallet.setUser(user);
        Category category = new Category();
        category.setId(7L);
        category.setUser(user);
        category.setType(Category.CategoryType.EXPENSE);
        when(extractionRepository.findByIdAndUserId(suggestionId, 42L))
                .thenReturn(Optional.of(suggestion));
        when(walletRepository.findById(5L)).thenReturn(Optional.of(wallet));
        when(categoryRepository.findById(7L)).thenReturn(Optional.of(category));

        AiSuggestionClarificationRequest request = new AiSuggestionClarificationRequest();
        request.setOccurredAt(LocalDateTime.of(2026, 10, 24, 9, 0));
        request.setSourceWalletId(5L);
        request.setAcknowledgePossibleDuplicate(true);

        var response = service.clarify(42L, suggestionId, request);

        assertEquals("PENDING", response.status());
        assertFalse(response.requiresClarification());
        assertEquals(5L, response.sourceWalletId());
        assertEquals("", suggestion.getAmbiguityReasons());
        verify(actionRepository).save(any(AiSuggestionAction.class));
    }

    @Test
    void confirmationUsesStoredSuggestionValuesAndNormalTransactionService() {
        suggestion.setAmbiguityReasons("");
        suggestion.setRequiresClarification(false);
        suggestion.setOccurredAt(LocalDateTime.of(2026, 10, 24, 9, 0));
        suggestion.setSourceWalletId(5L);
        when(extractionRepository.findByIdAndUserId(suggestionId, 42L))
                .thenReturn(Optional.of(suggestion));
        when(extractionRepository.findOneByIdAndUserId(suggestionId, 42L))
                .thenReturn(Optional.of(suggestion));
        when(transactionService.createTransaction(any(), any()))
                .thenAnswer(
                        invocation -> {
                            suggestion.setStatus("CONFIRMED");
                            suggestion.setConfirmedAt(clock.instant());
                            suggestion.setTransactionId(99L);
                            Transaction transaction = new Transaction();
                            transaction.setId(99L);
                            return transaction;
                        });

        AiSuggestionConfirmationRequest request = new AiSuggestionConfirmationRequest();
        request.setRememberAiCategory(true);
        AiSuggestionConfirmationResult result = service.confirm(42L, suggestionId, request);

        ArgumentCaptor<TransactionRequest> requestCaptor =
                ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactionService)
                .createTransaction(org.mockito.ArgumentMatchers.eq(42L), requestCaptor.capture());
        TransactionRequest transactionRequest = requestCaptor.getValue();
        assertEquals(5L, transactionRequest.getWalletId());
        assertEquals(new BigDecimal("125000.00"), transactionRequest.getAmount());
        assertEquals(7L, transactionRequest.getCategoryId());
        assertEquals(suggestionId, transactionRequest.getAiExtractionId());
        assertEquals(99L, result.suggestion().transactionId());
    }

    @Test
    void rejectionIsOwnedAndTerminal() {
        when(extractionRepository.findByIdAndUserId(suggestionId, 42L))
                .thenReturn(Optional.of(suggestion));

        var response =
                service.reject(
                        42L,
                        suggestionId,
                        new AiSuggestionRejectionRequest(
                                AiSuggestionRejectionRequest.Reason.DUPLICATE));

        assertEquals("REJECTED", response.status());
        assertEquals("DUPLICATE", response.rejectionReason());
        assertEquals(clock.instant(), response.resolvedAt());
        assertThrows(
                BadRequestException.class,
                () ->
                        service.reject(
                                42L,
                                suggestionId,
                                new AiSuggestionRejectionRequest(
                                        AiSuggestionRejectionRequest.Reason.INCORRECT)));
    }

    @Test
    void foreignSuggestionIsNotVisible() {
        when(extractionRepository.findOneByIdAndUserId(suggestionId, 42L))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.get(42L, suggestionId));
    }

    private AiExtraction suggestion() {
        AiExtraction extraction = new AiExtraction();
        extraction.setId(suggestionId);
        extraction.setUser(user);
        extraction.setSource("RECEIPT");
        extraction.setMerchantLabel("Cua hang mau");
        extraction.setMerchantKey("cua hang mau");
        extraction.setProposedCategoryId(7L);
        extraction.setTransactionType("EXPENSE");
        extraction.setAmount(new BigDecimal("125000.00"));
        extraction.setCurrency("VND");
        extraction.setAmbiguityReasons("occurred_at:MISSING,transaction:POSSIBLE_DUPLICATE");
        extraction.setRequiresClarification(true);
        extraction.setStatus("PENDING");
        extraction.setPromptVersion("receipt-v2");
        extraction.setModelVersion("model-test");
        extraction.setProvider("fake");
        extraction.setLocale("vi-VN");
        extraction.setTimeZone("Asia/Ho_Chi_Minh");
        extraction.setCreatedAt(clock.instant());
        extraction.setUpdatedAt(clock.instant());
        return extraction;
    }
}
