package com.wisekoin.backend.module.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wisekoin.backend.module.auth.dto.LoginRequest;
import com.wisekoin.backend.module.auth.jwt.JwtTokenProvider;
import com.wisekoin.backend.module.category.CategoryService;
import com.wisekoin.backend.module.common.exception.BadCredentialsException;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserService;
import com.wisekoin.backend.module.wallet.WalletService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {
    private UserService userService;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider jwtTokenProvider;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        authService =
                new AuthService(
                        userService,
                        passwordEncoder,
                        jwtTokenProvider,
                        mock(WalletService.class),
                        mock(CategoryService.class));
    }

    @Test
    void loginRejectsUnknownEmailAsBadCredentials() {
        LoginRequest request = loginRequest();
        when(userService.findUserByEmail(request.getEmail())).thenReturn(Optional.empty());

        BadCredentialsException exception =
                assertThrows(BadCredentialsException.class, () -> authService.login(request));

        assertEquals("Invalid credentials", exception.getMessage());
        verify(jwtTokenProvider, never()).generateToken(request.getEmail());
    }

    @Test
    void loginRejectsWrongPasswordAsBadCredentials() {
        LoginRequest request = loginRequest();
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword("encoded-password");
        when(userService.findUserByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPassword())).thenReturn(false);

        BadCredentialsException exception =
                assertThrows(BadCredentialsException.class, () -> authService.login(request));

        assertEquals("Invalid credentials", exception.getMessage());
        verify(jwtTokenProvider, never()).generateToken(request.getEmail());
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("wrong-password");
        return request;
    }
}
