package com.wisekoin.backend.module.ai.notification;

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
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "notification_pilot_metrics")
@Getter
@Setter
@NoArgsConstructor
public class NotificationPilotMetric {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(nullable = false, length = 30)
    private String source;

    @Column(name = "parser_version", nullable = false, length = 50)
    private String parserVersion;

    @Column(name = "observed_count", nullable = false)
    private long observedCount;

    @Column(name = "parsed_count", nullable = false)
    private long parsedCount;

    @Column(name = "parse_failure_count", nullable = false)
    private long parseFailureCount;

    @Column(name = "local_duplicate_count", nullable = false)
    private long localDuplicateCount;

    @Column(name = "uploaded_count", nullable = false)
    private long uploadedCount;

    @Column(name = "server_replay_count", nullable = false)
    private long serverReplayCount;

    @Column(name = "possible_duplicate_count", nullable = false)
    private long possibleDuplicateCount;

    @Column(name = "confirmed_count", nullable = false)
    private long confirmedCount;

    @Column(name = "rejected_false_positive_count", nullable = false)
    private long rejectedFalsePositiveCount;

    @Column(name = "rejected_duplicate_count", nullable = false)
    private long rejectedDuplicateCount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
