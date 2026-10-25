package com.wisekoin.backend.module.ai.notification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record NotificationMetricBatchRequest(
        @NotEmpty @Size(max = 20) List<@Valid NotificationMetricDelta> metrics) {}
