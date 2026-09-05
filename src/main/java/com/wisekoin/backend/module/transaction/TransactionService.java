package com.wisekoin.backend.module.transaction;

import com.wisekoin.backend.module.category.Category;
import com.wisekoin.backend.module.category.CategoryRepository;
import com.wisekoin.backend.module.common.event.TransactionCreatedEvent;
import com.wisekoin.backend.module.common.exception.BadRequestException;
import com.wisekoin.backend.module.common.exception.ResourceNotFoundException;
import com.wisekoin.backend.module.debt.Debt;
import com.wisekoin.backend.module.debt.DebtRepository;
import com.wisekoin.backend.module.goal.Goal;
import com.wisekoin.backend.module.goal.GoalRepository;
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
    private final DebtRepository debtRepository;
    private final GoalRepository goalRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Transaction createTransaction(Long userId, TransactionRequest request) {
        Wallet wallet = walletRepository.findById(request.getWalletId())
                .filter(w -> w.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found or access denied"));

        Category category = categoryRepository.findById(request.getCategoryId())
                .filter(c -> c.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Category not found or access denied"));

        if (request.getDebtId() != null && request.getGoalId() != null) {
            throw new BadRequestException("Transaction cannot be linked to both Debt and Goal");
        }

        if (request.getDebtId() != null) {
            Debt debt = debtRepository.findById(request.getDebtId())
                    .filter(d -> d.getUser().getId().equals(userId))
                    .orElseThrow(() -> new ResourceNotFoundException("Debt not found or access denied"));

            if (debt.getType() == Debt.DebtType.LEND && request.getType() != Transaction.TransactionType.INCOME) {
                throw new BadRequestException("LEND debt repayment must have INCOME type");
            }
            if (debt.getType() == Debt.DebtType.BORROW && request.getType() != Transaction.TransactionType.EXPENSE) {
                throw new BadRequestException("BORROW debt repayment must have EXPENSE type");
            }
        }

        if (request.getGoalId() != null) {
            Goal goal = goalRepository.findById(request.getGoalId())
                    .filter(g -> g.getUser().getId().equals(userId))
                    .orElseThrow(() -> new ResourceNotFoundException("Goal not found or access denied"));

            if (request.getType() != Transaction.TransactionType.TRANSFER) {
                throw new BadRequestException("Goal funding must use TRANSFER type");
            }
        }

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

        if (request.getDebtId() != null) {
            Debt debt = debtRepository.getReferenceById(request.getDebtId());
            newTransaction.setDebt(debt);
        }

        if (request.getGoalId() != null) {
            Goal goal = goalRepository.getReferenceById(request.getGoalId());
            newTransaction.setGoal(goal);
        }

        Transaction savedTransaction = transactionRepository.save(newTransaction);

        eventPublisher.publishEvent(new TransactionCreatedEvent(
                wallet.getId(),
                savedTransaction.getAmount(),
                savedTransaction.getType(),
                request.getDebtId(),
                request.getGoalId()
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
