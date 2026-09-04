package com.wisekoin.backend.module.transaction;

import com.wisekoin.backend.module.auth.security.CustomUserDetails;
import com.wisekoin.backend.module.transaction.dto.MonthlyStatsResponse;
import com.wisekoin.backend.module.transaction.dto.TransactionFilterRequest;
import com.wisekoin.backend.module.transaction.dto.TransactionRequest;
import com.wisekoin.backend.module.transaction.dto.TransactionResponse;
import com.wisekoin.backend.module.transaction.mapper.TransactionMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        Transaction newTransaction = transactionService.createTransaction(currentUser.getId(), request);
        return ResponseEntity.ok(transactionMapper.toResponse(newTransaction));
    }

    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> getMyTransactions(
            @ModelAttribute TransactionFilterRequest filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transactionDate"));

        Page<Transaction> transactions = transactionService.getFilteredTransactions(
                currentUser.getId(),
                filter,
                pageRequest
        );

        Page<TransactionResponse> responsePage = transactions.map(transactionMapper::toResponse);
        return ResponseEntity.ok(responsePage);
    }

    @GetMapping("/stats")
    public ResponseEntity<List<MonthlyStatsResponse>> getTransactionStats(
            @RequestParam int year,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        List<MonthlyStatsResponse> stats = transactionService.getYearlyStats(currentUser.getId(), year);
        return ResponseEntity.ok(stats);
    }

}
