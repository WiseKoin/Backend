package com.wisekoin.backend.module.transaction;

import com.wisekoin.backend.module.category.Category;
import com.wisekoin.backend.module.category.CategoryRepository;
import com.wisekoin.backend.module.common.event.TransactionCreatedEvent;
import com.wisekoin.backend.module.transaction.dto.TransactionRequest;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserRepository;
import com.wisekoin.backend.module.wallet.Wallet;
import com.wisekoin.backend.module.wallet.WalletRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final CategoryRepository categoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Transaction createTransaction(Long userId, TransactionRequest request) {
        Wallet wallet = walletRepository.findById(request.getWalletId())
                .filter(w -> w.getUser().getId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Wallet not found or access denied"));

        Category category = categoryRepository.findById(request.getCategoryId())
                .filter(c -> c.getUser().getId().equals(userId))
                .orElseThrow(() -> new RuntimeException("Category not found or access denied"));

        Transaction newTransaction = new Transaction();
        User userProxy = userRepository.getReferenceById(userId);

        newTransaction.setUser(userProxy);
        newTransaction.setWallet(wallet);
        newTransaction.setCategory(category);
        newTransaction.setAmount(request.getAmount());
        newTransaction.setType(request.getType());
        newTransaction.setNote(request.getNote());
        newTransaction.setTransactionDate(request.getTransactionDate());

        Transaction savedTransaction = transactionRepository.save(newTransaction);

        eventPublisher.publishEvent(new TransactionCreatedEvent(
                wallet.getId(),
                savedTransaction.getAmount(),
                savedTransaction.getType()
        ));

        return savedTransaction;
    }

    public Page<Transaction> getTransactionsByUser(Long userId, Pageable pageable) {
        return transactionRepository.findAllByUserIdOrderByTransactionDateDesc(userId, pageable);
    }

}
