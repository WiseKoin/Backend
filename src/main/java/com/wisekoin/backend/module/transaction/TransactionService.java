package com.wisekoin.backend.module.transaction;

import com.wisekoin.backend.module.category.Category;
import com.wisekoin.backend.module.category.CategoryRepository;
import com.wisekoin.backend.module.common.event.TransactionCreatedEvent;
import com.wisekoin.backend.module.transaction.dto.MonthlyStatsResponse;
import com.wisekoin.backend.module.transaction.dto.TransactionFilterRequest;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        newTransaction.setTransactionDate(
                request.getTransactionDate() != null
                        ? request.getTransactionDate()
                        : LocalDateTime.now()
        );

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

    public Page<Transaction> getFilteredTransactions(Long userId, TransactionFilterRequest filter, Pageable pageable) {
        Specification<Transaction> spec = TransactionSpecification.getFilterSpec(userId, filter);
        return transactionRepository.findAll(spec, pageable);
    }

    public List<MonthlyStatsResponse> getYearlyStats(Long userId, int year) {
        LocalDateTime startOfYear = LocalDateTime.of(year, Month.JANUARY, 1, 0, 0);
        LocalDateTime endOfYear = LocalDateTime.of(year, Month.DECEMBER, 31, 23, 59, 59);

        List<Transaction> yearlyTransactions = transactionRepository
                .findAllByUserIdAndTransactionDateBetween(userId, startOfYear, endOfYear);

        Map<Integer, List<Transaction>> transactionsByMonth = yearlyTransactions.stream()
                .collect(Collectors.groupingBy(tx -> tx.getTransactionDate().getMonthValue()));

        List<MonthlyStatsResponse> statsList = new ArrayList<>();

        for (int month = 1; month <= 12; month++) {
            List<Transaction> txInMonth = transactionsByMonth.getOrDefault(month, new ArrayList<>());

            BigDecimal totalIncome = txInMonth.stream()
                    .filter(tx -> tx.getType() == Transaction.TransactionType.INCOME)
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalExpense = txInMonth.stream()
                    .filter(tx -> tx.getType() == Transaction.TransactionType.EXPENSE)
                    .map(Transaction::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            statsList.add(new MonthlyStatsResponse(month, year, totalIncome, totalExpense));
        }

        return statsList;
    }

}
