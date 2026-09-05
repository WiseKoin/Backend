package com.wisekoin.backend.module.debt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DebtRepository extends JpaRepository<Debt, Long> {

    List<Debt> findAllByUserIdOrderByDueDateAsc(Long userId);

    List<Debt> findAllByUserIdAndStatusInOrderByDueDateAsc(Long userId, List<Debt.DebtStatus> statuses);

}
