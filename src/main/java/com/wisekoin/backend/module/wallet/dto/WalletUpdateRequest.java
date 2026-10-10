package com.wisekoin.backend.module.wallet.dto;

import com.wisekoin.backend.module.wallet.Wallet;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WalletUpdateRequest {
    private String name;
    private Wallet.WalletType walletType;

    @PositiveOrZero(message = "Balance cannot be negative")
    private BigDecimal balance;
}
