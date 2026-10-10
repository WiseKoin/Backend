package com.wisekoin.backend.module.recurring;

import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecurringTransactionScheduler {
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final RecurringTransactionProcessor recurringTransactionProcessor;
    private final Clock clock;

    @Scheduled(
            cron = "${app.recurring-transactions.cron:0 5 0 * * *}",
            zone = "${app.time-zone:UTC}")
    public void createDueTransactions() {
        LocalDate today = LocalDate.now(clock);
        for (Long recurringId : recurringTransactionRepository.findDueIds(today)) {
            try {
                recurringTransactionProcessor.process(recurringId, today);
            } catch (RuntimeException exception) {
                log.error("Could not process recurring transaction {}", recurringId, exception);
            }
        }
    }
}
