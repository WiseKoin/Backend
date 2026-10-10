package com.wisekoin.backend.module.wallet.mapper;

import com.wisekoin.backend.module.wallet.Wallet;
import com.wisekoin.backend.module.wallet.dto.WalletResponse;
import org.springframework.stereotype.Component;

@Component
public class WalletMapper {
    public WalletResponse toResponse(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(), wallet.getName(), wallet.getType(), wallet.getBalance());
    }
}
