package com.wisekoin.backend.module.recurring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RecurringTransactionServiceTest {
    @Test
    void clampsDayToEndOfShortMonth() {
        assertEquals(
                LocalDate.of(2027, 2, 28),
                RecurringTransactionService.nextOccurrence(LocalDate.of(2027, 2, 1), 31));
        assertEquals(
                LocalDate.of(2028, 2, 29),
                RecurringTransactionService.nextMonth(LocalDate.of(2028, 1, 31), 31));
    }

    @Test
    void movesToNextMonthWhenThisMonthsDayHasPassed() {
        assertEquals(
                LocalDate.of(2026, 11, 5),
                RecurringTransactionService.nextOccurrence(LocalDate.of(2026, 10, 10), 5));
    }

    @Test
    void permitsStartingOnTheDueDate() {
        assertEquals(
                LocalDate.of(2026, 10, 10),
                RecurringTransactionService.nextOccurrence(LocalDate.of(2026, 10, 10), 10));
    }
}
