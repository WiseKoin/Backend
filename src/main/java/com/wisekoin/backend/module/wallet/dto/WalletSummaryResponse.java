package com.wisekoin.backend.module.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class WalletSummaryResponse {

    private BigDecimal totalBalance;
    private List<WalletResponse> wallets;

}
