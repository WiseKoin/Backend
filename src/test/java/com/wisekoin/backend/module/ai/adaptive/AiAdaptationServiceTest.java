package com.wisekoin.backend.module.ai.adaptive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wisekoin.backend.module.category.Category;
import com.wisekoin.backend.module.category.CategoryRepository;
import com.wisekoin.backend.module.common.exception.ResourceNotFoundException;
import com.wisekoin.backend.module.transaction.Transaction;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiAdaptationServiceTest {
    private final AiExtractionRepository extractionRepository = mock(AiExtractionRepository.class);
    private final AiFeedbackRepository feedbackRepository = mock(AiFeedbackRepository.class);
    private final CategorizationRuleRepository ruleRepository =
            mock(CategorizationRuleRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC);
    private final AiAdaptationService service =
            new AiAdaptationService(
                    extractionRepository,
                    feedbackRepository,
                    ruleRepository,
                    categoryRepository,
                    userRepository,
                    new MerchantNormalizer(),
                    clock);

    private User user;
    private UUID extractionId;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(42L);
        extractionId = UUID.randomUUID();
        when(userRepository.getReferenceById(42L)).thenReturn(user);
        when(feedbackRepository.save(any(AiFeedback.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(ruleRepository.save(any(CategorizationRule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void confirmationStoresCorrectionAndCreatesOnlyExplicitRule() {
        AiExtraction extraction = extraction();
        Category groceries = category(8L, Category.CategoryType.EXPENSE);
        when(extractionRepository.findByIdAndUserId(extractionId, 42L))
                .thenReturn(Optional.of(extraction));
        when(categoryRepository.findById(8L)).thenReturn(Optional.of(groceries));
        when(ruleRepository.findByUserIdAndMerchantKeyAndTransactionType(
                        42L, "ca phe sua da", "EXPENSE"))
                .thenReturn(Optional.empty());

        service.confirmExtraction(42L, extractionId, 8L, Transaction.TransactionType.EXPENSE, true);

        ArgumentCaptor<AiFeedback> feedbackCaptor = ArgumentCaptor.forClass(AiFeedback.class);
        verify(feedbackRepository).save(feedbackCaptor.capture());
        assertEquals("CORRECTED", feedbackCaptor.getValue().getOutcome());
        assertEquals(8L, feedbackCaptor.getValue().getConfirmedCategoryId());
        assertEquals(true, feedbackCaptor.getValue().isRememberRule());

        ArgumentCaptor<CategorizationRule> ruleCaptor =
                ArgumentCaptor.forClass(CategorizationRule.class);
        verify(ruleRepository).save(ruleCaptor.capture());
        assertEquals("ca phe sua da", ruleCaptor.getValue().getMerchantKey());
        assertEquals(8L, ruleCaptor.getValue().getCategoryId());
        assertEquals("CONFIRMED", extraction.getStatus());
        assertEquals(clock.instant(), extraction.getConfirmedAt());
    }

    @Test
    void suggestionOwnershipIsEnforcedDuringConfirmation() {
        when(extractionRepository.findByIdAndUserId(extractionId, 42L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () ->
                        service.confirmExtraction(
                                42L, extractionId, 8L, Transaction.TransactionType.EXPENSE, true));
    }

    private AiExtraction extraction() {
        AiExtraction extraction = new AiExtraction();
        extraction.setId(extractionId);
        extraction.setUser(user);
        extraction.setMerchantKey("ca phe sua da");
        extraction.setProposedCategoryId(7L);
        extraction.setTransactionType("EXPENSE");
        extraction.setStatus("PENDING");
        return extraction;
    }

    private Category category(Long id, Category.CategoryType type) {
        Category category = new Category();
        category.setId(id);
        category.setUser(user);
        category.setType(type);
        return category;
    }
}
