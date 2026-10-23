package com.wisekoin.backend.module.transaction;

import com.wisekoin.backend.module.category.Category;
import com.wisekoin.backend.module.category.CategoryRepository;
import com.wisekoin.backend.module.common.event.TransactionCreatedEvent;
import com.wisekoin.backend.module.common.event.TransactionDeletedEvent;
import com.wisekoin.backend.module.common.event.TransactionUpdatedEvent;
import com.wisekoin.backend.module.common.exception.BadRequestException;
import com.wisekoin.backend.module.common.exception.ResourceNotFoundException;
import com.wisekoin.backend.module.debt.Debt;
import com.wisekoin.backend.module.debt.DebtRepository;
import com.wisekoin.backend.module.goal.Goal;
import com.wisekoin.backend.module.goal.GoalRepository;
import com.wisekoin.backend.module.transaction.dto.MonthlyStatsResponse;
import com.wisekoin.backend.module.transaction.dto.TransactionFilterRequest;
import com.wisekoin.backend.module.transaction.dto.TransactionRequest;
import com.wisekoin.backend.module.transaction.dto.TransactionUpdateRequest;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserRepository;
import com.wisekoin.backend.module.wallet.Wallet;
import com.wisekoin.backend.module.wallet.WalletRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

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
        Wallet wallet = getOwnedWallet(userId, request.getWalletId());
        Category category =
                resolveCategory(
                        userId, request.getCategoryId(), request.getGoalId(), request.getType());
        Wallet destinationWallet =
                validateLinks(
                        userId,
                        wallet.getId(),
                        request.getType(),
                        request.getDebtId(),
                        request.getGoalId(),
                        request.getDestinationWalletId());

        Transaction newTransaction = new Transaction();
        User userProxy = userRepository.getReferenceById(userId);

        newTransaction.setUser(userProxy);
        newTransaction.setWallet(wallet);
        newTransaction.setDestinationWallet(destinationWallet);
        newTransaction.setCategory(category);
        newTransaction.setAmount(request.getAmount());
        newTransaction.setType(request.getType());
        newTransaction.setNote(request.getNote());
        newTransaction.setTransactionDate(
                request.getTransactionDate() != null
                        ? request.getTransactionDate()
                        : LocalDateTime.now());

        if (request.getDebtId() != null) {
            Debt debt = debtRepository.getReferenceById(request.getDebtId());
            newTransaction.setDebt(debt);
        }

        if (request.getGoalId() != null) {
            Goal goal = goalRepository.getReferenceById(request.getGoalId());
            newTransaction.setGoal(goal);
        }

        Transaction savedTransaction = transactionRepository.save(newTransaction);

        eventPublisher.publishEvent(
                new TransactionCreatedEvent(
                        wallet.getId(),
                        request.getDestinationWalletId(),
                        savedTransaction.getAmount(),
                        savedTransaction.getType(),
                        request.getDebtId(),
                        request.getGoalId()));

        return savedTransaction;
    }

    @Transactional
    public Transaction updateTransaction(
            Long userId, Long transactionId, TransactionRequest request) {
        Transaction transaction = getOwnedTransaction(userId, transactionId);
        return updateTransaction(
                transaction,
                userId,
                request.getWalletId(),
                request.getCategoryId(),
                request.getAmount(),
                request.getType(),
                request.getNote(),
                request.getTransactionDate(),
                request.getDebtId(),
                request.getGoalId(),
                request.getDestinationWalletId());
    }

    @Transactional
    public Transaction patchTransaction(
            Long userId, Long transactionId, TransactionUpdateRequest request) {
        Transaction transaction = getOwnedTransaction(userId, transactionId);
        return updateTransaction(
                transaction,
                userId,
                request.getWalletId() != null
                        ? request.getWalletId()
                        : transaction.getWallet().getId(),
                request.getCategoryId() != null
                        ? request.getCategoryId()
                        : idOf(transaction.getCategory()),
                request.getAmount() != null ? request.getAmount() : transaction.getAmount(),
                request.getType() != null ? request.getType() : transaction.getType(),
                request.isNoteSet() ? request.getNote() : transaction.getNote(),
                request.getTransactionDate() != null
                        ? request.getTransactionDate()
                        : transaction.getTransactionDate(),
                request.isDebtIdSet() ? request.getDebtId() : idOf(transaction.getDebt()),
                request.isGoalIdSet() ? request.getGoalId() : idOf(transaction.getGoal()),
                request.isDestinationWalletIdSet()
                        ? request.getDestinationWalletId()
                        : idOf(transaction.getDestinationWallet()));
    }

    @Transactional
    public void deleteTransaction(Long userId, Long transactionId) {
        Transaction transaction = getOwnedTransaction(userId, transactionId);
        TransactionDeletedEvent event =
                new TransactionDeletedEvent(
                        transaction.getWallet().getId(), idOf(transaction.getDestinationWallet()),
                        transaction.getAmount(), transaction.getType(),
                        idOf(transaction.getDebt()), idOf(transaction.getGoal()));

        transactionRepository.delete(transaction);
        eventPublisher.publishEvent(event);
    }

    private Transaction updateTransaction(
            Transaction transaction,
            Long userId,
            Long walletId,
            Long categoryId,
            BigDecimal amount,
            Transaction.TransactionType type,
            String note,
            LocalDateTime transactionDate,
            Long debtId,
            Long goalId,
            Long destinationWalletId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Amount must be greater than 0");
        }
        if (type == null) {
            throw new BadRequestException("Transaction type is required");
        }

        Wallet wallet = getOwnedWallet(userId, walletId);
        Category category = resolveCategory(userId, categoryId, goalId, type);
        Wallet destinationWallet =
                validateLinks(userId, walletId, type, debtId, goalId, destinationWalletId);

        TransactionUpdatedEvent event =
                new TransactionUpdatedEvent(
                        transaction.getWallet().getId(),
                        idOf(transaction.getDestinationWallet()),
                        transaction.getAmount(),
                        transaction.getType(),
                        idOf(transaction.getDebt()),
                        idOf(transaction.getGoal()),
                        walletId,
                        destinationWalletId,
                        amount,
                        type,
                        debtId,
                        goalId);

        transaction.setWallet(wallet);
        transaction.setDestinationWallet(destinationWallet);
        transaction.setCategory(category);
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setNote(note);
        transaction.setTransactionDate(
                transactionDate != null ? transactionDate : LocalDateTime.now());
        transaction.setDebt(debtId != null ? debtRepository.getReferenceById(debtId) : null);
        transaction.setGoal(goalId != null ? goalRepository.getReferenceById(goalId) : null);

        Transaction saved = transactionRepository.save(transaction);
        eventPublisher.publishEvent(event);
        return saved;
    }

    private Transaction getOwnedTransaction(Long userId, Long transactionId) {
        return transactionRepository
                .findById(transactionId)
                .filter(t -> t.getUser().getId().equals(userId))
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Transaction not found or access denied"));
    }

    private Wallet getOwnedWallet(Long userId, Long walletId) {
        return walletRepository
                .findById(walletId)
                .filter(w -> w.getUser().getId().equals(userId))
                .orElseThrow(
                        () -> new ResourceNotFoundException("Wallet not found or access denied"));
    }

    private Category getOwnedCategory(Long userId, Long categoryId) {
        return categoryRepository
                .findById(categoryId)
                .filter(c -> c.getUser().getId().equals(userId))
                .orElseThrow(
                        () -> new ResourceNotFoundException("Category not found or access denied"));
    }

    private Category resolveCategory(
            Long userId,
            Long categoryId,
            Long goalId,
            Transaction.TransactionType transactionType) {
        if (transactionType == Transaction.TransactionType.TRANSFER) {
            return categoryId != null ? getOwnedCategory(userId, categoryId) : null;
        }
        if (goalId != null) {
            if (categoryId != null) {
                throw new BadRequestException("Goal funding must not have a category");
            }
            return null;
        }
        if (categoryId == null) {
            throw new BadRequestException("Category is required");
        }
        return getOwnedCategory(userId, categoryId);
    }

    private Wallet validateLinks(
            Long userId,
            Long walletId,
            Transaction.TransactionType type,
            Long debtId,
            Long goalId,
            Long destinationWalletId) {
        int linkedTargets =
                (debtId != null ? 1 : 0)
                        + (goalId != null ? 1 : 0)
                        + (destinationWalletId != null ? 1 : 0);
        if (linkedTargets > 1) {
            throw new BadRequestException(
                    "Transaction cannot be linked to more than one Debt, Goal, or destination Wallet");
        }
        if (debtId != null) {
            Debt debt =
                    debtRepository
                            .findById(debtId)
                            .filter(d -> d.getUser().getId().equals(userId))
                            .orElseThrow(
                                    () ->
                                            new ResourceNotFoundException(
                                                    "Debt not found or access denied"));
            if (debt.getType() == Debt.DebtType.LEND
                    && type != Transaction.TransactionType.INCOME) {
                throw new BadRequestException("LEND debt repayment must have INCOME type");
            }
            if (debt.getType() == Debt.DebtType.BORROW
                    && type != Transaction.TransactionType.EXPENSE) {
                throw new BadRequestException("BORROW debt repayment must have EXPENSE type");
            }
        }
        if (goalId != null) {
            goalRepository
                    .findById(goalId)
                    .filter(g -> g.getUser().getId().equals(userId))
                    .orElseThrow(
                            () -> new ResourceNotFoundException("Goal not found or access denied"));
            if (type != Transaction.TransactionType.TRANSFER) {
                throw new BadRequestException("Goal funding must use TRANSFER type");
            }
        }
        Wallet destinationWallet = null;
        if (destinationWalletId != null) {
            destinationWallet = getOwnedWallet(userId, destinationWalletId);
            if (type != Transaction.TransactionType.TRANSFER) {
                throw new BadRequestException("Wallet-to-wallet transfers must use TRANSFER type");
            }
            if (walletId.equals(destinationWalletId)) {
                throw new BadRequestException("Source and destination wallets must be different");
            }
        }
        if (type == Transaction.TransactionType.TRANSFER
                && goalId == null
                && destinationWalletId == null) {
            throw new BadRequestException("TRANSFER requires a Goal or destination Wallet");
        }
        if (type != Transaction.TransactionType.TRANSFER && destinationWalletId != null) {
            throw new BadRequestException(
                    "Only TRANSFER transactions can have a destination Wallet");
        }
        return destinationWallet;
    }

    private Long idOf(Debt debt) {
        return debt == null ? null : debt.getId();
    }

    private Long idOf(Goal goal) {
        return goal == null ? null : goal.getId();
    }

    private Long idOf(Wallet wallet) {
        return wallet == null ? null : wallet.getId();
    }

    private Long idOf(Category category) {
        return category == null ? null : category.getId();
    }

    public Page<Transaction> getTransactionsByUser(Long userId, Pageable pageable) {
        return transactionRepository.findAllByUserIdOrderByTransactionDateDesc(userId, pageable);
    }

    public Page<Transaction> getFilteredTransactions(
            Long userId, TransactionFilterRequest filter, Pageable pageable) {
        Specification<Transaction> spec = TransactionSpecification.getFilterSpec(userId, filter);
        return transactionRepository.findAll(spec, pageable);
    }

    public List<MonthlyStatsResponse> getYearlyStats(Long userId, int year) {
        LocalDateTime startOfYear = LocalDateTime.of(year, Month.JANUARY, 1, 0, 0);
        LocalDateTime endOfYear = LocalDateTime.of(year, Month.DECEMBER, 31, 23, 59, 59);

        List<Transaction> yearlyTransactions =
                transactionRepository.findAllByUserIdAndTransactionDateBetween(
                        userId, startOfYear, endOfYear);

        Map<Integer, List<Transaction>> transactionsByMonth =
                yearlyTransactions.stream()
                        .collect(
                                Collectors.groupingBy(
                                        tx -> tx.getTransactionDate().getMonthValue()));

        List<MonthlyStatsResponse> statsList = new ArrayList<>();

        for (int month = 1; month <= 12; month++) {
            List<Transaction> txInMonth =
                    transactionsByMonth.getOrDefault(month, new ArrayList<>());

            BigDecimal totalIncome =
                    txInMonth.stream()
                            .filter(tx -> tx.getType() == Transaction.TransactionType.INCOME)
                            .map(Transaction::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalExpense =
                    txInMonth.stream()
                            .filter(
                                    tx ->
                                            tx.getType() == Transaction.TransactionType.EXPENSE
                                                    || (tx.getType()
                                                                    == Transaction.TransactionType
                                                                            .TRANSFER
                                                            && tx.getGoal() != null))
                            .map(Transaction::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            statsList.add(new MonthlyStatsResponse(month, year, totalIncome, totalExpense));
        }

        return statsList;
    }
}
