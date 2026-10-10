package com.wisekoin.backend.module.wallet.dto;

import com.wisekoin.backend.module.wallet.Wallet;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class WalletResponse {
    private Long id;
    private String name;
    private Wallet.WalletType type;
    private BigDecimal balance;
}
