package com.wisekoin.backend.module.goal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, Long> {
    List<Goal> findAllByUserIdOrderByDeadlineAsc(Long userId);

    List<Goal> findAllByUserIdAndStatusOrderByDeadlineAsc(Long userId, Goal.GoalStatus status);
}
