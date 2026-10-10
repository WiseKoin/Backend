package com.wisekoin.backend.module.recurring;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, Long> {
    @EntityGraph(attributePaths = {"wallet", "category"})
    List<RecurringTransaction> findAllByUserIdOrderByNextRunDateAsc(Long userId);

    @EntityGraph(attributePaths = {"wallet", "category"})
    Optional<RecurringTransaction> findByIdAndUserId(Long id, Long userId);

    @Query(
            "select recurring.id from RecurringTransaction recurring "
                    + "where recurring.active = true and recurring.nextRunDate <= :date "
                    + "order by recurring.nextRunDate, recurring.id")
    List<Long> findDueIds(@Param("date") LocalDate date);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"wallet", "category"})
    @Query("select recurring from RecurringTransaction recurring where recurring.id = :id")
    Optional<RecurringTransaction> findByIdForUpdate(@Param("id") Long id);

    boolean existsByWalletId(Long walletId);

    boolean existsByCategoryId(Long categoryId);

    @Modifying
    @Query("delete from RecurringTransaction recurring where recurring.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
