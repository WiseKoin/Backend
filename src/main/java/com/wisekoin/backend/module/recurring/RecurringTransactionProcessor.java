package com.wisekoin.backend.module.recurring;

import com.wisekoin.backend.module.category.Category;
import com.wisekoin.backend.module.common.exception.BadRequestException;
import com.wisekoin.backend.module.transaction.Transaction;
import com.wisekoin.backend.module.transaction.TransactionService;
import com.wisekoin.backend.module.transaction.dto.TransactionRequest;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecurringTransactionProcessor {
    private static final int MAX_OCCURRENCES_PER_RUN = 120;

    private final RecurringTransactionRepository recurringTransactionRepository;
    private final TransactionService transactionService;

    public RecurringTransactionProcessor(
            RecurringTransactionRepository recurringTransactionRepository,
            TransactionService transactionService) {
        this.recurringTransactionRepository = recurringTransactionRepository;
        this.transactionService = transactionService;
    }

    @Transactional
    public int process(Long recurringId, LocalDate throughDate) {
        RecurringTransaction recurring =
                recurringTransactionRepository.findByIdForUpdate(recurringId).orElse(null);
        if (recurring == null
                || !recurring.isActive()
                || recurring.getNextRunDate().isAfter(throughDate)) {
            return 0;
        }
        if (recurring.getCategory().getType() != Category.CategoryType.EXPENSE) {
            throw new BadRequestException("Recurring transaction category is no longer EXPENSE");
        }

        int processed = 0;
        while (!recurring.getNextRunDate().isAfter(throughDate)
                && processed < MAX_OCCURRENCES_PER_RUN) {
            LocalDate dueDate = recurring.getNextRunDate();
            TransactionRequest request = new TransactionRequest();
            request.setWalletId(recurring.getWallet().getId());
            request.setCategoryId(recurring.getCategory().getId());
            request.setAmount(recurring.getAmount());
            request.setType(Transaction.TransactionType.EXPENSE);
            request.setNote(recurring.getNote());
            request.setTransactionDate(dueDate.atStartOfDay());
            transactionService.createTransaction(recurring.getUser().getId(), request);

            recurring.setNextRunDate(
                    RecurringTransactionService.nextMonth(dueDate, recurring.getDayOfMonth()));
            processed++;
        }
        recurringTransactionRepository.save(recurring);
        return processed;
    }
}
