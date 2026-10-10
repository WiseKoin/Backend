package com.wisekoin.backend.module.transaction;

import com.wisekoin.backend.module.auth.security.CustomUserDetails;
import com.wisekoin.backend.module.transaction.dto.MonthlyStatsResponse;
import com.wisekoin.backend.module.transaction.dto.TransactionFilterRequest;
import com.wisekoin.backend.module.transaction.dto.TransactionRequest;
import com.wisekoin.backend.module.transaction.dto.TransactionResponse;
import com.wisekoin.backend.module.transaction.dto.TransactionUpdateRequest;
import com.wisekoin.backend.module.transaction.mapper.TransactionMapper;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {
    private final TransactionService transactionService;
    private final TransactionMapper transactionMapper;

    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @Valid @RequestBody TransactionRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        Transaction newTransaction =
                transactionService.createTransaction(currentUser.getId(), request);
        return ResponseEntity.ok(transactionMapper.toResponse(newTransaction));
    }

    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> getMyTransactions(
            @ModelAttribute TransactionFilterRequest filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        PageRequest pageRequest =
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transactionDate"));

        Page<Transaction> transactions =
                transactionService.getFilteredTransactions(
                        currentUser.getId(), filter, pageRequest);

        Page<TransactionResponse> responsePage = transactions.map(transactionMapper::toResponse);
        return ResponseEntity.ok(responsePage);
    }

    @GetMapping("/stats")
    public ResponseEntity<List<MonthlyStatsResponse>> getTransactionStats(
            @RequestParam int year, @AuthenticationPrincipal CustomUserDetails currentUser) {
        List<MonthlyStatsResponse> stats =
                transactionService.getYearlyStats(currentUser.getId(), year);
        return ResponseEntity.ok(stats);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponse> updateTransaction(
            @PathVariable Long id,
            @Valid @RequestBody TransactionRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(
                transactionMapper.toResponse(
                        transactionService.updateTransaction(currentUser.getId(), id, request)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TransactionResponse> patchTransaction(
            @PathVariable Long id,
            @Valid @RequestBody TransactionUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(
                transactionMapper.toResponse(
                        transactionService.patchTransaction(currentUser.getId(), id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(
            @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails currentUser) {
        transactionService.deleteTransaction(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
