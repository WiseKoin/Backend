package com.wisekoin.backend.module.wallet;

import com.wisekoin.backend.module.common.exception.InsufficientBalanceException;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;

    public Wallet createWallet(Long userId, String name, Wallet.WalletType walletType, BigDecimal initialBalance) {
        Wallet newWallet = new Wallet();
        User userProxy = userRepository.getReferenceById(userId);

        newWallet.setUser(userProxy);
        newWallet.setName(name);
        newWallet.setType(walletType);
        newWallet.setBalance(initialBalance);

        return walletRepository.save(newWallet);
    }

    public List<Wallet> getWalletsByUser(Long userId) {
        return walletRepository.findAllByUserId(userId);
    }

    @Transactional
    public void updateBalance(Long walletId, BigDecimal amount) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        BigDecimal newBalance = wallet.getBalance().add(amount);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientBalanceException("Insufficient balance in the wallet");
        }

        wallet.setBalance(newBalance);
        walletRepository.save(wallet);
    }

}
