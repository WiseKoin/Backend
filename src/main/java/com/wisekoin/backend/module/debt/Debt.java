package com.wisekoin.backend.module.debt;

import com.wisekoin.backend.module.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "debts")
@Getter
@Setter
@NoArgsConstructor
public class Debt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String counterpartName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DebtType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal remainingAmount;

    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DebtStatus status;

    @Column(columnDefinition = "TEXT")
    private String note;

    public enum DebtType {
        LEND, BORROW
    }

    public enum DebtStatus {
        PENDING, COMPLETED, OVERDUE
    }

}
