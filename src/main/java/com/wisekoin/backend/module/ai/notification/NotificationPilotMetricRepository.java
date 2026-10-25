package com.wisekoin.backend.module.ai.notification;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPilotMetricRepository
        extends JpaRepository<NotificationPilotMetric, Long> {
    Optional<NotificationPilotMetric> findByUserIdAndMetricDateAndSourceAndParserVersion(
            Long userId, LocalDate metricDate, String source, String parserVersion);

    List<NotificationPilotMetric> findAllByUserIdAndMetricDateBetweenOrderByMetricDateDesc(
            Long userId, LocalDate from, LocalDate to);
}
