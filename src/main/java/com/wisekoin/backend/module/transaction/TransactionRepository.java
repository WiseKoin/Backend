package com.wisekoin.backend.module.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends
        JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction>
{

    Page<Transaction> findAllByUserIdOrderByTransactionDateDesc(Long userId, Pageable pageable);

    List<Transaction> findAllByUserIdAndTransactionDateBetween(
            Long userId, LocalDateTime startDate, LocalDateTime endDate);

    boolean existsByWalletId(Long walletId);

    boolean existsByDestinationWalletId(Long walletId);

    boolean existsByCategoryId(Long categoryId);

    boolean existsByDebtId(Long debtId);

    boolean existsByGoalId(Long goalId);

}
