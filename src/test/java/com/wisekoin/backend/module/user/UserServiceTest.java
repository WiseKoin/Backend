package com.wisekoin.backend.module.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wisekoin.backend.module.auth.token.RefreshTokenService;
import com.wisekoin.backend.module.category.CategoryRepository;
import com.wisekoin.backend.module.common.exception.BadCredentialsException;
import com.wisekoin.backend.module.common.exception.BadRequestException;
import com.wisekoin.backend.module.debt.DebtRepository;
import com.wisekoin.backend.module.goal.GoalRepository;
import com.wisekoin.backend.module.recurring.RecurringTransactionRepository;
import com.wisekoin.backend.module.transaction.TransactionRepository;
import com.wisekoin.backend.module.wallet.WalletRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private RefreshTokenService refreshTokenService;
    private RecurringTransactionRepository recurringTransactionRepository;
    private TransactionRepository transactionRepository;
    private DebtRepository debtRepository;
    private GoalRepository goalRepository;
    private CategoryRepository categoryRepository;
    private WalletRepository walletRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        refreshTokenService = mock(RefreshTokenService.class);
        recurringTransactionRepository = mock(RecurringTransactionRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        debtRepository = mock(DebtRepository.class);
        goalRepository = mock(GoalRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        walletRepository = mock(WalletRepository.class);
        userService =
                new UserService(
                        userRepository,
                        passwordEncoder,
                        refreshTokenService,
                        recurringTransactionRepository,
                        transactionRepository,
                        debtRepository,
                        goalRepository,
                        categoryRepository,
                        walletRepository);
    }

    @Test
    void changePasswordRehashesPasswordAndRevokesSessions() {
        User user = user();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-password", user.getPassword())).thenReturn(true);
        when(passwordEncoder.matches("new-password", user.getPassword())).thenReturn(false);
        when(passwordEncoder.encode("new-password")).thenReturn("new-password-hash");

        userService.changePassword(user.getId(), "current-password", "new-password");

        assertEquals("new-password-hash", user.getPassword());
        verify(userRepository).save(user);
        verify(refreshTokenService).revokeAllForUser(user.getId());
    }

    @Test
    void changePasswordRejectsIncorrectCurrentPassword() {
        User user = user();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", user.getPassword())).thenReturn(false);

        assertThrows(
                BadCredentialsException.class,
                () -> userService.changePassword(user.getId(), "wrong-password", "new-password"));

        verify(userRepository, never()).save(user);
        verify(refreshTokenService, never()).revokeAllForUser(user.getId());
    }

    @Test
    void updateBaseCurrencyNormalizesValidIsoCode() {
        User user = user();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        User updated = userService.updateBaseCurrency(user.getId(), "usd");

        assertEquals("USD", updated.getBaseCurrency());
    }

    @Test
    void updateBaseCurrencyRejectsUnknownIsoCode() {
        User user = user();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThrows(
                BadRequestException.class,
                () -> userService.updateBaseCurrency(user.getId(), "ZZZ"));
    }

    @Test
    void deleteAccountRemovesOwnedDataInForeignKeySafeOrder() {
        User user = user();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-password", user.getPassword())).thenReturn(true);

        userService.deleteAccount(user.getId(), "current-password");

        InOrder deletionOrder =
                inOrder(
                        refreshTokenService,
                        recurringTransactionRepository,
                        transactionRepository,
                        debtRepository,
                        goalRepository,
                        categoryRepository,
                        walletRepository,
                        userRepository);
        deletionOrder.verify(refreshTokenService).revokeAllForUser(user.getId());
        deletionOrder.verify(recurringTransactionRepository).deleteAllByUserId(user.getId());
        deletionOrder.verify(transactionRepository).deleteAllByUserId(user.getId());
        deletionOrder.verify(debtRepository).deleteAllByUserId(user.getId());
        deletionOrder.verify(goalRepository).deleteAllByUserId(user.getId());
        deletionOrder.verify(categoryRepository).deleteAllByUserId(user.getId());
        deletionOrder.verify(walletRepository).deleteAllByUserId(user.getId());
        deletionOrder.verify(userRepository).delete(user);
    }

    private User user() {
        User user = new User();
        user.setId(7L);
        user.setEmail("user@example.com");
        user.setPassword("current-password-hash");
        return user;
    }
}
