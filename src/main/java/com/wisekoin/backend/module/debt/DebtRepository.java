package com.wisekoin.backend.module.debt;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DebtRepository extends JpaRepository<Debt, Long> {
    List<Debt> findAllByUserIdOrderByDueDateAsc(Long userId);

    List<Debt> findAllByUserIdAndStatusInOrderByDueDateAsc(
            Long userId, List<Debt.DebtStatus> statuses);
}
