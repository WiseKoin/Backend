package com.wisekoin.backend.module.user;

import com.wisekoin.backend.module.auth.token.RefreshTokenService;
import com.wisekoin.backend.module.category.CategoryRepository;
import com.wisekoin.backend.module.common.exception.BadCredentialsException;
import com.wisekoin.backend.module.common.exception.BadRequestException;
import com.wisekoin.backend.module.common.exception.ResourceNotFoundException;
import com.wisekoin.backend.module.debt.DebtRepository;
import com.wisekoin.backend.module.goal.GoalRepository;
import com.wisekoin.backend.module.recurring.RecurringTransactionRepository;
import com.wisekoin.backend.module.transaction.TransactionRepository;
import com.wisekoin.backend.module.wallet.WalletRepository;
import jakarta.transaction.Transactional;
import java.util.Currency;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final TransactionRepository transactionRepository;
    private final DebtRepository debtRepository;
    private final GoalRepository goalRepository;
    private final CategoryRepository categoryRepository;
    private final WalletRepository walletRepository;

    public User getUserByEmail(String email) {
        return userRepository
                .findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public Optional<User> findUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Transactional
    public User createUser(String email, String encodedPassword, String username, String phone) {
        User newUser = new User();
        newUser.setEmail(email);
        newUser.setPassword(encodedPassword);
        newUser.setUsername(username);
        newUser.setPhone(phone);

        return userRepository.save(newUser);
    }

    @Transactional
    public User updateUserProfile(String email, User updatedUser) {
        User user = getUserByEmail(email);

        user.setEmail(updatedUser.getEmail());
        user.setUsername(updatedUser.getUsername());
        user.setPhone(updatedUser.getPhone());

        return userRepository.save(user);
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = getUserById(userId);
        verifyPassword(user, currentPassword);
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BadRequestException("New password must be different from current password");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(userId);
    }

    @Transactional
    public User updateBaseCurrency(Long userId, String baseCurrency) {
        User user = getUserById(userId);
        String normalizedCurrency = baseCurrency.trim().toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(normalizedCurrency);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unsupported ISO 4217 currency code");
        }

        user.setBaseCurrency(normalizedCurrency);
        return userRepository.save(user);
    }

    @Transactional
    public void deleteAccount(Long userId, String currentPassword) {
        User user = getUserById(userId);
        verifyPassword(user, currentPassword);

        refreshTokenService.revokeAllForUser(userId);
        recurringTransactionRepository.deleteAllByUserId(userId);
        transactionRepository.deleteAllByUserId(userId);
        debtRepository.deleteAllByUserId(userId);
        goalRepository.deleteAllByUserId(userId);
        categoryRepository.deleteAllByUserId(userId);
        walletRepository.deleteAllByUserId(userId);
        userRepository.delete(user);
    }

    private User getUserById(Long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private void verifyPassword(User user, String currentPassword) {
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }
    }
}
