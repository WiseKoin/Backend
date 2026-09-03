package com.wisekoin.backend.module.wallet.dto;

import com.wisekoin.backend.module.wallet.Wallet;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class WalletRequest {

    @NotBlank
    private String name;

    @NotNull
    private Wallet.WalletType walletType;

    @NotNull
    private BigDecimal initialBalance;

}
