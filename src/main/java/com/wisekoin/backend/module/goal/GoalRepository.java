package com.wisekoin.backend.module.goal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    List<Goal> findAllByUserIdOrderByDeadlineAsc(Long userId);

    List<Goal> findAllByUserIdAndStatusOrderByDeadlineAsc(Long userId, Goal.GoalStatus status);

}
