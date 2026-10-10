package com.wisekoin.backend.module.recurring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wisekoin.backend.module.category.Category;
import com.wisekoin.backend.module.transaction.TransactionService;
import com.wisekoin.backend.module.transaction.dto.TransactionRequest;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.wallet.Wallet;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RecurringTransactionProcessorTest {
    @Test
    void catchesUpMissedMonthlyExpensesAndAdvancesSchedule() {
        RecurringTransactionRepository repository = mock(RecurringTransactionRepository.class);
        TransactionService transactionService = mock(TransactionService.class);
        RecurringTransactionProcessor processor =
                new RecurringTransactionProcessor(repository, transactionService);
        RecurringTransaction recurring = recurringTransaction();
        when(repository.findByIdForUpdate(44L)).thenReturn(Optional.of(recurring));

        int processed = processor.process(44L, LocalDate.of(2026, 3, 31));

        assertEquals(3, processed);
        assertEquals(LocalDate.of(2026, 4, 30), recurring.getNextRunDate());
        ArgumentCaptor<TransactionRequest> requests =
                ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactionService, times(3)).createTransaction(eq(7L), requests.capture());
        assertEquals(
                LocalDate.of(2026, 1, 31).atStartOfDay(),
                requests.getAllValues().getFirst().getTransactionDate());
        assertEquals(
                LocalDate.of(2026, 3, 31).atStartOfDay(),
                requests.getAllValues().getLast().getTransactionDate());
        verify(repository).save(recurring);
    }

    private RecurringTransaction recurringTransaction() {
        User user = new User();
        user.setId(7L);
        Wallet wallet = new Wallet();
        wallet.setId(8L);
        wallet.setUser(user);
        Category category = new Category();
        category.setId(9L);
        category.setUser(user);
        category.setType(Category.CategoryType.EXPENSE);

        RecurringTransaction recurring = new RecurringTransaction();
        recurring.setId(44L);
        recurring.setUser(user);
        recurring.setWallet(wallet);
        recurring.setCategory(category);
        recurring.setAmount(new BigDecimal("10.00"));
        recurring.setNote("Rent");
        recurring.setDayOfMonth(31);
        recurring.setNextRunDate(LocalDate.of(2026, 1, 31));
        recurring.setActive(true);
        return recurring;
    }
}
