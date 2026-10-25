package com.wisekoin.backend.module.ai.suggestion;

import com.wisekoin.backend.module.ai.notification.BankNotificationSuggestionService;
import com.wisekoin.backend.module.ai.notification.NotificationPilotMetricService;
import com.wisekoin.backend.module.ai.notification.dto.BankNotificationSuggestionRequest;
import com.wisekoin.backend.module.ai.notification.dto.NotificationMetricBatchRequest;
import com.wisekoin.backend.module.ai.notification.dto.NotificationMetricResponse;
import com.wisekoin.backend.module.ai.suggestion.dto.AiSuggestionClarificationRequest;
import com.wisekoin.backend.module.ai.suggestion.dto.AiSuggestionConfirmationRequest;
import com.wisekoin.backend.module.ai.suggestion.dto.AiSuggestionConfirmationResponse;
import com.wisekoin.backend.module.ai.suggestion.dto.AiSuggestionRejectionRequest;
import com.wisekoin.backend.module.ai.suggestion.dto.AiSuggestionResponse;
import com.wisekoin.backend.module.auth.security.CustomUserDetails;
import com.wisekoin.backend.module.transaction.mapper.TransactionMapper;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/suggestions")
@RequiredArgsConstructor
public class AiSuggestionController {
    private final AiSuggestionService suggestionService;
    private final TransactionMapper transactionMapper;
    private final BankNotificationSuggestionService bankNotificationSuggestionService;
    private final NotificationPilotMetricService notificationMetricService;

    @GetMapping
    public ResponseEntity<Page<AiSuggestionResponse>> list(
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        int boundedSize = Math.min(Math.max(size, 1), 100);
        PageRequest pageRequest =
                PageRequest.of(
                        Math.max(page, 0), boundedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(suggestionService.list(currentUser.getId(), status, pageRequest));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AiSuggestionResponse> get(
            @PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(suggestionService.get(currentUser.getId(), id));
    }

    @PostMapping("/notifications")
    public ResponseEntity<AiSuggestionResponse> createNotificationSuggestion(
            @Valid @RequestBody BankNotificationSuggestionRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(
                bankNotificationSuggestionService.create(currentUser.getId(), request));
    }

    @PostMapping("/notifications/metrics")
    public ResponseEntity<Void> recordNotificationMetrics(
            @Valid @RequestBody NotificationMetricBatchRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        notificationMetricService.recordClientMetrics(currentUser.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/notifications/metrics")
    public ResponseEntity<List<NotificationMetricResponse>> getNotificationMetrics(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(
                notificationMetricService.getMetrics(currentUser.getId(), from, to));
    }

    @PatchMapping("/{id}/clarification")
    public ResponseEntity<AiSuggestionResponse> clarify(
            @PathVariable UUID id,
            @Valid @RequestBody AiSuggestionClarificationRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(suggestionService.clarify(currentUser.getId(), id, request));
    }

    @PostMapping("/{id}/confirmation")
    public ResponseEntity<AiSuggestionConfirmationResponse> confirm(
            @PathVariable UUID id,
            @Valid @RequestBody AiSuggestionConfirmationRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        AiSuggestionConfirmationResult result =
                suggestionService.confirm(currentUser.getId(), id, request);
        return ResponseEntity.ok(
                new AiSuggestionConfirmationResponse(
                        result.suggestion(), transactionMapper.toResponse(result.transaction())));
    }

    @PostMapping("/{id}/rejection")
    public ResponseEntity<AiSuggestionResponse> reject(
            @PathVariable UUID id,
            @Valid @RequestBody AiSuggestionRejectionRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(suggestionService.reject(currentUser.getId(), id, request));
    }
}
