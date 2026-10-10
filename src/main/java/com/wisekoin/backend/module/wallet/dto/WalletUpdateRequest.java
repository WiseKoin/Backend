package com.wisekoin.backend.module.wallet.dto;

import com.wisekoin.backend.module.wallet.Wallet;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class WalletUpdateRequest {
    private String name;
    private Wallet.WalletType walletType;

    @PositiveOrZero(message = "Balance cannot be negative")
    private BigDecimal balance;
}
