package com.wisekoin.backend.module.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wisekoin.backend.module.auth.dto.LoginRequest;
import com.wisekoin.backend.module.auth.dto.RefreshTokenRequest;
import com.wisekoin.backend.module.auth.jwt.JwtTokenProvider;
import com.wisekoin.backend.module.auth.token.RefreshTokenService;
import com.wisekoin.backend.module.auth.token.RefreshTokenService.RefreshSession;
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
    private RefreshTokenService refreshTokenService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        refreshTokenService = mock(RefreshTokenService.class);
        authService =
                new AuthService(
                        userService,
                        passwordEncoder,
                        jwtTokenProvider,
                        refreshTokenService,
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

    @Test
    void loginReturnsAccessAndRefreshTokens() {
        LoginRequest request = loginRequest();
        User user = user(request.getEmail());
        when(userService.findUserByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.getPassword(), user.getPassword())).thenReturn(true);
        when(refreshTokenService.issue(user)).thenReturn("refresh-token");
        when(jwtTokenProvider.generateToken(user.getEmail())).thenReturn("access-token");
        when(jwtTokenProvider.getExpirationSeconds()).thenReturn(900L);

        var response = authService.login(request);

        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(900L, response.getExpiresIn());
    }

    @Test
    void refreshRotatesRefreshTokenAndReturnsNewAccessToken() {
        User user = user("user@example.com");
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("current-refresh-token");
        when(refreshTokenService.rotate(request.getRefreshToken()))
                .thenReturn(new RefreshSession(user, "new-refresh-token"));
        when(jwtTokenProvider.generateToken(user.getEmail())).thenReturn("new-access-token");

        var response = authService.refresh(request);

        assertEquals("new-access-token", response.getAccessToken());
        assertEquals("new-refresh-token", response.getRefreshToken());
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("wrong-password");
        return request;
    }

    private User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("encoded-password");
        return user;
    }
}
