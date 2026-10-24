package com.wisekoin.backend.module.ai.suggestion;

import com.wisekoin.backend.module.ai.adaptive.AiExtraction;
import com.wisekoin.backend.module.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "ai_suggestion_actions")
@Getter
@Setter
@NoArgsConstructor
public class AiSuggestionAction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "extraction_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AiExtraction extraction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency;

    @Column(name = "transaction_type", length = 20)
    private String transactionType;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "occurred_at")
    private LocalDateTime occurredAt;

    @Column(name = "source_wallet_id")
    private Long sourceWalletId;

    @Column(name = "destination_wallet_id")
    private Long destinationWalletId;

    @Column(name = "reason_code", length = 30)
    private String reasonCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
