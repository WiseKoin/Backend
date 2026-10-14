package com.wisekoin.backend.module.user.otp;

import com.wisekoin.backend.module.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "user_otp_challenges",
        uniqueConstraints = {@UniqueConstraint(columnNames = {"user_id", "purpose"})})
@Getter
@Setter
@NoArgsConstructor
public class OtpChallenge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OtpPurpose purpose;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "resend_available_at", nullable = false)
    private Instant resendAvailableAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "send_count", nullable = false)
    private int sendCount;

    @Column(name = "send_window_started_at", nullable = false)
    private Instant sendWindowStartedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verification_token_hash", length = 64)
    private String verificationTokenHash;

    @Column(name = "verification_expires_at")
    private Instant verificationExpiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
