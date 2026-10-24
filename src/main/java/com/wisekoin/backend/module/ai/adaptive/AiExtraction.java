package com.wisekoin.backend.module.ai.adaptive;

import com.wisekoin.backend.module.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "ai_extractions")
@Getter
@Setter
@NoArgsConstructor
public class AiExtraction {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "merchant_key", length = 200)
    private String merchantKey;

    @Column(name = "merchant_label", length = 200)
    private String merchantLabel;

    @Column(name = "proposed_category_id")
    private Long proposedCategoryId;

    @Column(name = "transaction_type", nullable = false, length = 20)
    private String transactionType;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency;

    @Column(name = "occurred_at")
    private LocalDateTime occurredAt;

    @Column(name = "source_wallet_id")
    private Long sourceWalletId;

    @Column(name = "destination_wallet_id")
    private Long destinationWalletId;

    @Column(name = "ambiguity_reasons", nullable = false, length = 1000)
    private String ambiguityReasons;

    @Column(nullable = false)
    private boolean clarified;

    @Column(name = "prompt_version", nullable = false, length = 100)
    private String promptVersion;

    @Column(name = "model_version", nullable = false, length = 150)
    private String modelVersion;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "confidence_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "confidence_level", nullable = false, length = 20)
    private String confidenceLevel;

    @Column(name = "confidence_reasons", nullable = false, length = 1000)
    private String confidenceReasons;

    @Column(name = "confidence_profile", nullable = false, length = 100)
    private String confidenceProfile;

    @Column(name = "confidence_calibrated", nullable = false)
    private boolean confidenceCalibrated;

    @Column(name = "rule_applied", nullable = false)
    private boolean ruleApplied;

    @Column(name = "requires_clarification", nullable = false)
    private boolean requiresClarification;

    @Column(nullable = false, length = 35)
    private String locale;

    @Column(name = "time_zone", nullable = false, length = 100)
    private String timeZone;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejection_reason", length = 30)
    private String rejectionReason;

    @Column(name = "transaction_id")
    private Long transactionId;
}
