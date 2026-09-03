package com.wisekoin.backend.module.wallet.dto;

import com.wisekoin.backend.module.wallet.Wallet;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
public class WalletResponse {

    private Long id;
    private String name;
    private Wallet.WalletType type;
    private BigDecimal balance;

}
