package com.wisekoin.backend.module.debt;

import com.wisekoin.backend.module.auth.security.CustomUserDetails;
import com.wisekoin.backend.module.debt.dto.DebtRepaymentRequest;
import com.wisekoin.backend.module.debt.dto.DebtRequest;
import com.wisekoin.backend.module.debt.dto.DebtResponse;
import com.wisekoin.backend.module.debt.dto.DebtUpdateRequest;
import com.wisekoin.backend.module.debt.mapper.DebtMapper;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/debts")
@RequiredArgsConstructor
@Slf4j
public class DebtController {
    private final DebtService debtService;
    private final DebtMapper debtMapper;

    @PostMapping
    public ResponseEntity<DebtResponse> createDebt(
            @Valid @RequestBody DebtRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        Debt debt = debtService.createDebt(currentUser.getId(), request);
        return ResponseEntity.ok(debtMapper.toResponse(debt));
    }

    @GetMapping
    public ResponseEntity<List<DebtResponse>> getDebts(
            @RequestParam(required = false) Debt.DebtStatus status,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        List<Debt> debts = debtService.getDebts(currentUser.getId(), status);
        List<DebtResponse> responses = debts.stream().map(debtMapper::toResponse).toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{id}/repay")
    public ResponseEntity<DebtResponse> repayDebt(
            @PathVariable Long id,
            @Valid @RequestBody DebtRepaymentRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        Debt debt = debtService.repay(currentUser.getId(), id, request);
        return ResponseEntity.ok(debtMapper.toResponse(debt));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DebtResponse> updateDebt(
            @PathVariable Long id,
            @Valid @RequestBody DebtRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(
                debtMapper.toResponse(debtService.updateDebt(currentUser.getId(), id, request)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<DebtResponse> patchDebt(
            @PathVariable Long id,
            @Valid @RequestBody DebtUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(
                debtMapper.toResponse(debtService.patchDebt(currentUser.getId(), id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDebt(
            @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails currentUser) {
        debtService.deleteDebt(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
