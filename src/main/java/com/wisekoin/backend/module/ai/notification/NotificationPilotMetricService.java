package com.wisekoin.backend.module.ai.notification;

import com.wisekoin.backend.module.ai.adaptive.AiExtraction;
import com.wisekoin.backend.module.ai.notification.dto.NotificationMetricBatchRequest;
import com.wisekoin.backend.module.ai.notification.dto.NotificationMetricDelta;
import com.wisekoin.backend.module.ai.notification.dto.NotificationMetricResponse;
import com.wisekoin.backend.module.ai.suggestion.dto.AiSuggestionRejectionRequest;
import com.wisekoin.backend.module.common.exception.BadRequestException;
import com.wisekoin.backend.module.common.exception.ResourceNotFoundException;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationPilotMetricService {
    private static final int MAX_REPORT_AGE_DAYS = 30;

    private final NotificationPilotMetricRepository repository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public void recordClientMetrics(Long userId, NotificationMetricBatchRequest request) {
        User user = lockedUser(userId);
        for (NotificationMetricDelta delta : request.metrics()) {
            BankNotificationSource source = BankNotificationSource.from(delta.source());
            source.validateParserVersion(delta.parserVersion());
            validateDate(delta.metricDate());
            if (delta.parsed() + delta.parseFailures() > delta.observed()) {
                throw new BadRequestException(
                        "Parsed and failed counts cannot exceed observed notifications");
            }
            if (delta.localDuplicates() > delta.parsed()) {
                throw new BadRequestException(
                        "Local duplicate count cannot exceed parsed notifications");
            }
            NotificationPilotMetric metric =
                    getOrCreate(user, delta.metricDate(), source, delta.parserVersion());
            metric.setObservedCount(add(metric.getObservedCount(), delta.observed()));
            metric.setParsedCount(add(metric.getParsedCount(), delta.parsed()));
            metric.setParseFailureCount(add(metric.getParseFailureCount(), delta.parseFailures()));
            metric.setLocalDuplicateCount(
                    add(metric.getLocalDuplicateCount(), delta.localDuplicates()));
            save(metric);
        }
    }

    @Transactional
    public void recordUpload(
            Long userId,
            BankNotificationSource source,
            String parserVersion,
            boolean replay,
            int possibleDuplicates) {
        User user = lockedUser(userId);
        NotificationPilotMetric metric =
                getOrCreate(user, LocalDate.now(clock), source, parserVersion);
        if (replay) {
            metric.setServerReplayCount(add(metric.getServerReplayCount(), 1));
        } else {
            metric.setUploadedCount(add(metric.getUploadedCount(), 1));
            metric.setPossibleDuplicateCount(
                    add(metric.getPossibleDuplicateCount(), possibleDuplicates));
        }
        save(metric);
    }

    @Transactional
    public void recordConfirmed(Long userId, AiExtraction extraction) {
        if (!isNotification(extraction)) {
            return;
        }
        NotificationPilotMetric metric = metricForExtraction(userId, extraction);
        metric.setConfirmedCount(add(metric.getConfirmedCount(), 1));
        save(metric);
    }

    @Transactional
    public void recordRejected(
            Long userId, AiExtraction extraction, AiSuggestionRejectionRequest.Reason reason) {
        if (!isNotification(extraction)) {
            return;
        }
        NotificationPilotMetric metric = metricForExtraction(userId, extraction);
        if (reason == AiSuggestionRejectionRequest.Reason.DUPLICATE) {
            metric.setRejectedDuplicateCount(add(metric.getRejectedDuplicateCount(), 1));
        } else if (reason == AiSuggestionRejectionRequest.Reason.INCORRECT
                || reason == AiSuggestionRejectionRequest.Reason.NOT_RELEVANT) {
            metric.setRejectedFalsePositiveCount(add(metric.getRejectedFalsePositiveCount(), 1));
        }
        save(metric);
    }

    @Transactional
    public List<NotificationMetricResponse> getMetrics(Long userId, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        LocalDate effectiveTo = to == null ? today : to;
        LocalDate effectiveFrom = from == null ? effectiveTo.minusDays(13) : from;
        if (effectiveTo.isAfter(today)
                || effectiveFrom.isAfter(effectiveTo)
                || effectiveFrom.isBefore(today.minusDays(MAX_REPORT_AGE_DAYS))) {
            throw new BadRequestException("Notification metric range is invalid");
        }
        return repository
                .findAllByUserIdAndMetricDateBetweenOrderByMetricDateDesc(
                        userId, effectiveFrom, effectiveTo)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private NotificationPilotMetric metricForExtraction(Long userId, AiExtraction extraction) {
        User user = lockedUser(userId);
        BankNotificationSource source = BankNotificationSource.from(extraction.getMerchantLabel());
        String parserVersion = extraction.getParserVersion();
        source.validateParserVersion(parserVersion);
        LocalDate date = extraction.getCreatedAt().atZone(clock.getZone()).toLocalDate();
        return getOrCreate(user, date, source, parserVersion);
    }

    private NotificationPilotMetric getOrCreate(
            User user, LocalDate date, BankNotificationSource source, String parserVersion) {
        return repository
                .findByUserIdAndMetricDateAndSourceAndParserVersion(
                        user.getId(), date, source.name(), parserVersion)
                .orElseGet(
                        () -> {
                            NotificationPilotMetric metric = new NotificationPilotMetric();
                            metric.setUser(user);
                            metric.setMetricDate(date);
                            metric.setSource(source.name());
                            metric.setParserVersion(parserVersion);
                            return metric;
                        });
    }

    private User lockedUser(Long userId) {
        return userRepository
                .findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private void validateDate(LocalDate date) {
        LocalDate today = LocalDate.now(clock);
        if (date.isAfter(today) || date.isBefore(today.minusDays(MAX_REPORT_AGE_DAYS))) {
            throw new BadRequestException("Notification metric date is out of range");
        }
    }

    private boolean isNotification(AiExtraction extraction) {
        return "BANK_NOTIFICATION".equals(extraction.getSource());
    }

    private long add(long current, long delta) {
        return Math.addExact(current, delta);
    }

    private void save(NotificationPilotMetric metric) {
        metric.setUpdatedAt(clock.instant());
        repository.save(metric);
    }

    private NotificationMetricResponse toResponse(NotificationPilotMetric metric) {
        return new NotificationMetricResponse(
                metric.getMetricDate(),
                metric.getSource(),
                metric.getParserVersion(),
                metric.getObservedCount(),
                metric.getParsedCount(),
                metric.getParseFailureCount(),
                metric.getLocalDuplicateCount(),
                metric.getUploadedCount(),
                metric.getServerReplayCount(),
                metric.getPossibleDuplicateCount(),
                metric.getConfirmedCount(),
                metric.getRejectedFalsePositiveCount(),
                metric.getRejectedDuplicateCount(),
                rate(metric.getParsedCount(), metric.getObservedCount()),
                rate(
                        metric.getRejectedFalsePositiveCount(),
                        metric.getConfirmedCount()
                                + metric.getRejectedFalsePositiveCount()
                                + metric.getRejectedDuplicateCount()));
    }

    private BigDecimal rate(long numerator, long denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(4);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }
}
