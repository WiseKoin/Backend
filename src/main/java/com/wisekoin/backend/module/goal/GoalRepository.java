package com.wisekoin.backend.module.goal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoalRepository extends JpaRepository<Goal, Long> {
    List<Goal> findAllByUserIdOrderByDeadlineAsc(Long userId);

    List<Goal> findAllByUserIdAndStatusOrderByDeadlineAsc(Long userId, Goal.GoalStatus status);

    @Modifying
    @Query("delete from Goal goal where goal.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
