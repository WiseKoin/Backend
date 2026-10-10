package com.wisekoin.backend.module.wallet;

import com.wisekoin.backend.module.common.exception.InsufficientBalanceException;
import com.wisekoin.backend.module.common.exception.BadRequestException;
import com.wisekoin.backend.module.common.exception.ResourceNotFoundException;
import com.wisekoin.backend.module.transaction.TransactionRepository;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserRepository;
import com.wisekoin.backend.module.wallet.dto.WalletRequest;
import com.wisekoin.backend.module.wallet.dto.WalletUpdateRequest;
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
    private final TransactionRepository transactionRepository;

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
        List<Wallet> wallets = walletRepository.findAllByUserId(userId);
        if (wallets.isEmpty()) {
            createDefaultWallet(userId);
            wallets = walletRepository.findAllByUserId(userId);
        }
        return wallets;
    }

    public Wallet createDefaultWallet(Long userId) {
        return createWallet(userId, "Default", Wallet.WalletType.CASH, BigDecimal.ZERO);
    }

    @Transactional
    public Wallet updateWallet(Long userId, Long walletId, WalletRequest request) {
        Wallet wallet = getOwnedWallet(userId, walletId);
        wallet.setName(request.getName());
        wallet.setType(request.getWalletType());
        wallet.setBalance(request.getInitialBalance());
        return walletRepository.save(wallet);
    }

    @Transactional
    public Wallet patchWallet(Long userId, Long walletId, WalletUpdateRequest request) {
        Wallet wallet = getOwnedWallet(userId, walletId);
        if (request.getName() != null) {
            if (request.getName().isBlank()) {
                throw new BadRequestException("Wallet name cannot be blank");
            }
            wallet.setName(request.getName());
        }
        if (request.getWalletType() != null) {
            wallet.setType(request.getWalletType());
        }
        if (request.getBalance() != null) {
            wallet.setBalance(request.getBalance());
        }
        return walletRepository.save(wallet);
    }

    @Transactional
    public void deleteWallet(Long userId, Long walletId) {
        Wallet wallet = getOwnedWallet(userId, walletId);
        if (transactionRepository.existsByWalletId(walletId)) {
            throw new BadRequestException("Cannot delete a wallet that is used by transactions");
        }
        walletRepository.delete(wallet);
    }

    private Wallet getOwnedWallet(Long userId, Long walletId) {
        return walletRepository.findById(walletId)
                .filter(wallet -> wallet.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found or access denied"));
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
