package com.wisekoin.backend.module.goal;

import com.wisekoin.backend.module.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "goals")
@Getter
@Setter
@NoArgsConstructor
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal targetAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal currentAmount;

    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GoalStatus status;

    public enum GoalStatus {
        IN_PROGRESS, ACHIEVED, CANCELLED
    }

}
