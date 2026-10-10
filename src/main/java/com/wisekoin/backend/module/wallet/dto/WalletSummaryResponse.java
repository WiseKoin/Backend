package com.wisekoin.backend.module.wallet.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class WalletSummaryResponse {
    private BigDecimal totalBalance;
    private List<WalletResponse> wallets;
}
