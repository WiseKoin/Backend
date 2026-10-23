package com.wisekoin.backend.module.ai.adaptive;

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
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "ai_feedback")
@Getter
@Setter
@NoArgsConstructor
public class AiFeedback {
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

    @Column(name = "proposed_category_id")
    private Long proposedCategoryId;

    @Column(name = "confirmed_category_id")
    private Long confirmedCategoryId;

    @Column(name = "confirmed_transaction_type", nullable = false, length = 20)
    private String confirmedTransactionType;

    @Column(nullable = false, length = 20)
    private String outcome;

    @Column(name = "remember_rule", nullable = false)
    private boolean rememberRule;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
