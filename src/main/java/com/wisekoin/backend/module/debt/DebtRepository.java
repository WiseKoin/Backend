package com.wisekoin.backend.module.debt;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DebtRepository extends JpaRepository<Debt, Long> {
    List<Debt> findAllByUserIdOrderByDueDateAsc(Long userId);

    List<Debt> findAllByUserIdAndStatusInOrderByDueDateAsc(
            Long userId, List<Debt.DebtStatus> statuses);

    @Modifying
    @Query("delete from Debt debt where debt.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
