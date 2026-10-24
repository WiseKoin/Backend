package com.wisekoin.backend.module.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wisekoin.backend.module.ai.adaptive.AiAdaptationService;
import com.wisekoin.backend.module.category.CategoryRepository;
import com.wisekoin.backend.module.debt.DebtRepository;
import com.wisekoin.backend.module.goal.GoalRepository;
import com.wisekoin.backend.module.transaction.dto.TransactionRequest;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserRepository;
import com.wisekoin.backend.module.wallet.Wallet;
import com.wisekoin.backend.module.wallet.WalletRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class TransactionServiceTest {
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final WalletRepository walletRepository = mock(WalletRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final DebtRepository debtRepository = mock(DebtRepository.class);
    private final GoalRepository goalRepository = mock(GoalRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final AiAdaptationService aiAdaptationService = mock(AiAdaptationService.class);
    private final TransactionService service =
            new TransactionService(
                    transactionRepository,
                    userRepository,
                    walletRepository,
                    categoryRepository,
                    debtRepository,
                    goalRepository,
                    eventPublisher,
                    aiAdaptationService);

    @Test
    void walletTransferAllowsNoCategoryAndKeepsOwnedDestination() {
        User user = new User();
        user.setId(42L);
        Wallet source = wallet(1L, user);
        Wallet destination = wallet(2L, user);
        when(walletRepository.findById(1L)).thenReturn(Optional.of(source));
        when(walletRepository.findById(2L)).thenReturn(Optional.of(destination));
        when(userRepository.getReferenceById(42L)).thenReturn(user);
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionRequest request = new TransactionRequest();
        request.setWalletId(1L);
        request.setDestinationWalletId(2L);
        request.setAmount(new BigDecimal("250000"));
        request.setType(Transaction.TransactionType.TRANSFER);
        UUID extractionId = UUID.randomUUID();
        request.setAiExtractionId(extractionId);

        Transaction transaction = service.createTransaction(42L, request);

        assertNull(transaction.getCategory());
        assertEquals(2L, transaction.getDestinationWallet().getId());
        assertEquals(Transaction.TransactionType.TRANSFER, transaction.getType());
        verify(aiAdaptationService)
                .confirmExtraction(
                        42L,
                        extractionId,
                        null,
                        Transaction.TransactionType.TRANSFER,
                        false,
                        transaction.getId());
    }

    private Wallet wallet(Long id, User user) {
        Wallet wallet = new Wallet();
        wallet.setId(id);
        wallet.setUser(user);
        wallet.setName("Wallet " + id);
        wallet.setBalance(BigDecimal.TEN);
        return wallet;
    }
}
