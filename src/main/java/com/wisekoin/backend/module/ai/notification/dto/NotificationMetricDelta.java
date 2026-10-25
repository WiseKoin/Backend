package com.wisekoin.backend.module.ai.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record NotificationMetricDelta(
        @NotBlank @Pattern(regexp = "VCB|MB|TECHCOMBANK|MOMO") String source,
        @JsonProperty("parser_version") @NotBlank @Size(max = 50) String parserVersion,
        @JsonProperty("metric_date") @NotNull LocalDate metricDate,
        @Min(0) @Max(10000) long observed,
        @Min(0) @Max(10000) long parsed,
        @JsonProperty("parse_failures") @Min(0) @Max(10000) long parseFailures,
        @JsonProperty("local_duplicates") @Min(0) @Max(10000) long localDuplicates) {}
