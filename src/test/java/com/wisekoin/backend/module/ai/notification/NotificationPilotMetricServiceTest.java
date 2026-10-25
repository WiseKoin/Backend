package com.wisekoin.backend.module.ai.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wisekoin.backend.module.ai.notification.dto.NotificationMetricBatchRequest;
import com.wisekoin.backend.module.ai.notification.dto.NotificationMetricDelta;
import com.wisekoin.backend.module.common.exception.BadRequestException;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationPilotMetricServiceTest {
    private final NotificationPilotMetricRepository repository =
            mock(NotificationPilotMetricRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-25T03:00:00Z"), ZoneOffset.UTC);
    private final NotificationPilotMetricService service =
            new NotificationPilotMetricService(repository, userRepository, clock);

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(42L);
        when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
        when(repository.findByUserIdAndMetricDateAndSourceAndParserVersion(
                        any(), any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void storesBoundedAggregateCountersWithoutNotificationContent() {
        service.recordClientMetrics(
                42L,
                new NotificationMetricBatchRequest(
                        List.of(
                                new NotificationMetricDelta(
                                        "VCB", "vcb-v1", LocalDate.of(2026, 10, 25), 5, 3, 2, 1))));

        ArgumentCaptor<NotificationPilotMetric> captor =
                ArgumentCaptor.forClass(NotificationPilotMetric.class);
        verify(repository).save(captor.capture());
        assertEquals(5, captor.getValue().getObservedCount());
        assertEquals(3, captor.getValue().getParsedCount());
        assertEquals(2, captor.getValue().getParseFailureCount());
        assertEquals(1, captor.getValue().getLocalDuplicateCount());
    }

    @Test
    void rejectsInconsistentCoverageCounters() {
        var request =
                new NotificationMetricBatchRequest(
                        List.of(
                                new NotificationMetricDelta(
                                        "VCB", "vcb-v1", LocalDate.of(2026, 10, 25), 1, 1, 1, 0)));

        assertThrows(BadRequestException.class, () -> service.recordClientMetrics(42L, request));
    }

    @Test
    void rejectsLocalDuplicatesThatExceedParsedCount() {
        var request =
                new NotificationMetricBatchRequest(
                        List.of(
                                new NotificationMetricDelta(
                                        "VCB", "vcb-v1", LocalDate.of(2026, 10, 25), 2, 1, 1, 2)));

        assertThrows(BadRequestException.class, () -> service.recordClientMetrics(42L, request));
    }
}
