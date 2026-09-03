package com.wisekoin.backend.module.wallet;

import com.wisekoin.backend.module.auth.security.CustomUserDetails;
import com.wisekoin.backend.module.wallet.dto.WalletRequest;
import com.wisekoin.backend.module.wallet.dto.WalletResponse;
import com.wisekoin.backend.module.wallet.dto.WalletSummaryResponse;
import com.wisekoin.backend.module.wallet.mapper.WalletMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
@Slf4j
public class WalletController {

    private final WalletService walletService;
    private final WalletMapper walletMapper;

    @PostMapping
    public ResponseEntity<WalletResponse> createWallet(
            @Valid @RequestBody WalletRequest walletRequest,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        Wallet newWallet = walletService.createWallet(
                currentUser.getId(),
                walletRequest.getName(),
                walletRequest.getWalletType(),
                walletRequest.getInitialBalance()
        );

        return ResponseEntity.ok(walletMapper.toResponse(newWallet));
    }

    @GetMapping
    public ResponseEntity<WalletSummaryResponse> getMyWallets(
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        List<Wallet> wallets = walletService.getWalletsByUser(currentUser.getId());

        List<WalletResponse> walletResponses = wallets.stream()
                .map(walletMapper::toResponse)
                .toList();

        BigDecimal totalBalance = wallets.stream()
                .map(Wallet::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        WalletSummaryResponse walletSummaryResponse = new WalletSummaryResponse(totalBalance, walletResponses);

        return ResponseEntity.ok(walletSummaryResponse);
    }

}
