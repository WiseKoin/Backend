package com.wisekoin.backend.module.transaction;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository
        extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    @EntityGraph(attributePaths = {"wallet", "category", "debt", "goal", "destinationWallet"})
    Page<Transaction> findAllByUserIdOrderByTransactionDateDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"wallet", "category", "debt", "goal", "destinationWallet"})
    List<Transaction> findAllByUserIdAndTransactionDateBetween(
            Long userId, LocalDateTime startDate, LocalDateTime endDate);

    @Override
    @EntityGraph(attributePaths = {"wallet", "category", "debt", "goal", "destinationWallet"})
    Page<Transaction> findAll(Specification<Transaction> spec, Pageable pageable);

    boolean existsByWalletId(Long walletId);

    boolean existsByDestinationWalletId(Long walletId);

    boolean existsByCategoryId(Long categoryId);

    boolean existsByDebtId(Long debtId);

    boolean existsByGoalId(Long goalId);

    @Modifying
    @Query("delete from Transaction transaction where transaction.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
