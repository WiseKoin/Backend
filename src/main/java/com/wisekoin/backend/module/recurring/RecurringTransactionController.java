package com.wisekoin.backend.module.recurring;

import com.wisekoin.backend.module.auth.security.CustomUserDetails;
import com.wisekoin.backend.module.recurring.dto.RecurringTransactionRequest;
import com.wisekoin.backend.module.recurring.dto.RecurringTransactionResponse;
import com.wisekoin.backend.module.recurring.dto.RecurringTransactionUpdateRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recurring-transactions")
@RequiredArgsConstructor
public class RecurringTransactionController {
    private final RecurringTransactionService recurringTransactionService;
    private final RecurringTransactionMapper recurringTransactionMapper;

    @PostMapping
    public ResponseEntity<RecurringTransactionResponse> create(
            @Valid @RequestBody RecurringTransactionRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(
                recurringTransactionMapper.toResponse(
                        recurringTransactionService.create(currentUser.getId(), request)));
    }

    @GetMapping
    public ResponseEntity<List<RecurringTransactionResponse>> getAll(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(
                recurringTransactionService.getAll(currentUser.getId()).stream()
                        .map(recurringTransactionMapper::toResponse)
                        .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecurringTransactionResponse> get(
            @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(
                recurringTransactionMapper.toResponse(
                        recurringTransactionService.get(currentUser.getId(), id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecurringTransactionResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody RecurringTransactionRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(
                recurringTransactionMapper.toResponse(
                        recurringTransactionService.update(currentUser.getId(), id, request)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<RecurringTransactionResponse> patch(
            @PathVariable Long id,
            @Valid @RequestBody RecurringTransactionUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(
                recurringTransactionMapper.toResponse(
                        recurringTransactionService.patch(currentUser.getId(), id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails currentUser) {
        recurringTransactionService.delete(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
