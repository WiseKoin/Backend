package com.wisekoin.backend.module.recurring;

import com.wisekoin.backend.module.category.Category;
import com.wisekoin.backend.module.category.CategoryRepository;
import com.wisekoin.backend.module.common.exception.BadRequestException;
import com.wisekoin.backend.module.common.exception.ResourceNotFoundException;
import com.wisekoin.backend.module.recurring.dto.RecurringTransactionRequest;
import com.wisekoin.backend.module.recurring.dto.RecurringTransactionUpdateRequest;
import com.wisekoin.backend.module.user.UserRepository;
import com.wisekoin.backend.module.wallet.Wallet;
import com.wisekoin.backend.module.wallet.WalletRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class RecurringTransactionService {
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final CategoryRepository categoryRepository;
    private final Clock clock;

    public RecurringTransaction create(Long userId, RecurringTransactionRequest request) {
        Wallet wallet = getOwnedWallet(userId, request.getWalletId());
        Category category = getExpenseCategory(userId, request.getCategoryId());
        LocalDate startDate = request.getStartDate() != null ? request.getStartDate() : today();

        RecurringTransaction recurring = new RecurringTransaction();
        recurring.setUser(userRepository.getReferenceById(userId));
        recurring.setWallet(wallet);
        recurring.setCategory(category);
        recurring.setAmount(request.getAmount());
        recurring.setNote(request.getNote());
        recurring.setDayOfMonth(request.getDayOfMonth());
        recurring.setNextRunDate(nextOccurrence(startDate, request.getDayOfMonth()));
        recurring.setActive(request.isActive());
        recurring.setCreatedAt(LocalDateTime.now(clock));
        return recurringTransactionRepository.save(recurring);
    }

    @Transactional(readOnly = true)
    public List<RecurringTransaction> getAll(Long userId) {
        return recurringTransactionRepository.findAllByUserIdOrderByNextRunDateAsc(userId);
    }

    @Transactional(readOnly = true)
    public RecurringTransaction get(Long userId, Long recurringId) {
        return getOwned(userId, recurringId);
    }

    public RecurringTransaction update(
            Long userId, Long recurringId, RecurringTransactionRequest request) {
        RecurringTransaction recurring = getOwned(userId, recurringId);
        recurring.setWallet(getOwnedWallet(userId, request.getWalletId()));
        recurring.setCategory(getExpenseCategory(userId, request.getCategoryId()));
        recurring.setAmount(request.getAmount());
        recurring.setNote(request.getNote());
        recurring.setDayOfMonth(request.getDayOfMonth());
        LocalDate startDate = request.getStartDate() != null ? request.getStartDate() : today();
        recurring.setNextRunDate(nextOccurrence(startDate, request.getDayOfMonth()));
        recurring.setActive(request.isActive());
        return recurringTransactionRepository.save(recurring);
    }

    public RecurringTransaction patch(
            Long userId, Long recurringId, RecurringTransactionUpdateRequest request) {
        RecurringTransaction recurring = getOwned(userId, recurringId);
        if (request.getWalletId() != null) {
            recurring.setWallet(getOwnedWallet(userId, request.getWalletId()));
        }
        if (request.getCategoryId() != null) {
            recurring.setCategory(getExpenseCategory(userId, request.getCategoryId()));
        }
        if (request.getAmount() != null) {
            recurring.setAmount(request.getAmount());
        }
        if (request.isNoteSet()) {
            recurring.setNote(request.getNote());
        }

        boolean scheduleChanged = request.getDayOfMonth() != null || request.getStartDate() != null;
        if (request.getDayOfMonth() != null) {
            recurring.setDayOfMonth(request.getDayOfMonth());
        }
        if (scheduleChanged) {
            LocalDate startDate = request.getStartDate() != null ? request.getStartDate() : today();
            recurring.setNextRunDate(nextOccurrence(startDate, recurring.getDayOfMonth()));
        }

        if (request.getActive() != null) {
            boolean resuming = request.getActive() && !recurring.isActive();
            recurring.setActive(request.getActive());
            if (resuming && !scheduleChanged) {
                recurring.setNextRunDate(nextOccurrence(today(), recurring.getDayOfMonth()));
            }
        }
        return recurringTransactionRepository.save(recurring);
    }

    public void delete(Long userId, Long recurringId) {
        recurringTransactionRepository.delete(getOwned(userId, recurringId));
    }

    static LocalDate nextOccurrence(LocalDate startDate, int dayOfMonth) {
        YearMonth month = YearMonth.from(startDate);
        LocalDate candidate = month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
        if (candidate.isBefore(startDate)) {
            YearMonth nextMonth = month.plusMonths(1);
            return nextMonth.atDay(Math.min(dayOfMonth, nextMonth.lengthOfMonth()));
        }
        return candidate;
    }

    static LocalDate nextMonth(LocalDate current, int dayOfMonth) {
        YearMonth nextMonth = YearMonth.from(current).plusMonths(1);
        return nextMonth.atDay(Math.min(dayOfMonth, nextMonth.lengthOfMonth()));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private RecurringTransaction getOwned(Long userId, Long recurringId) {
        return recurringTransactionRepository
                .findByIdAndUserId(recurringId, userId)
                .orElseThrow(
                        () ->
                                new ResourceNotFoundException(
                                        "Recurring transaction not found or access denied"));
    }

    private Wallet getOwnedWallet(Long userId, Long walletId) {
        return walletRepository
                .findById(walletId)
                .filter(wallet -> wallet.getUser().getId().equals(userId))
                .orElseThrow(
                        () -> new ResourceNotFoundException("Wallet not found or access denied"));
    }

    private Category getExpenseCategory(Long userId, Long categoryId) {
        Category category =
                categoryRepository
                        .findById(categoryId)
                        .filter(candidate -> candidate.getUser().getId().equals(userId))
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Category not found or access denied"));
        if (category.getType() != Category.CategoryType.EXPENSE) {
            throw new BadRequestException("Recurring expenses require an EXPENSE category");
        }
        return category;
    }
}
