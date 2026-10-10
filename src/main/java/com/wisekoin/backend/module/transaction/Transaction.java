package com.wisekoin.backend.module.transaction;

import com.wisekoin.backend.module.category.Category;
import com.wisekoin.backend.module.goal.Goal;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.wallet.Wallet;
import com.wisekoin.backend.module.debt.Debt;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_wallet_id")
    private Wallet destinationWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "debt_id")
    private Debt debt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id")
    private Goal goal;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(nullable = false)
    private LocalDateTime transactionDate;

    public enum TransactionType {
        INCOME, EXPENSE, TRANSFER
    }

}
