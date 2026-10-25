package com.wisekoin.backend.module.ai.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;

public record NotificationMetricResponse(
        LocalDate date,
        String source,
        @JsonProperty("parser_version") String parserVersion,
        long observed,
        long parsed,
        @JsonProperty("parse_failures") long parseFailures,
        @JsonProperty("local_duplicates") long localDuplicates,
        long uploaded,
        @JsonProperty("server_replays") long serverReplays,
        @JsonProperty("possible_duplicates") long possibleDuplicates,
        long confirmed,
        @JsonProperty("rejected_false_positives") long rejectedFalsePositives,
        @JsonProperty("rejected_duplicates") long rejectedDuplicates,
        @JsonProperty("parser_coverage") BigDecimal parserCoverage,
        @JsonProperty("false_positive_rate") BigDecimal falsePositiveRate) {}
