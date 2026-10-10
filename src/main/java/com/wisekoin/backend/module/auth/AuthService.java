package com.wisekoin.backend.module.auth;

import com.wisekoin.backend.module.auth.dto.AuthResponse;
import com.wisekoin.backend.module.auth.dto.LoginRequest;
import com.wisekoin.backend.module.auth.dto.RegisterRequest;
import com.wisekoin.backend.module.auth.jwt.JwtTokenProvider;
import com.wisekoin.backend.module.category.CategoryService;
import com.wisekoin.backend.module.common.exception.BadCredentialsException;
import com.wisekoin.backend.module.common.exception.BadRequestException;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserService;
import com.wisekoin.backend.module.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final WalletService walletService;
    private final CategoryService categoryService;

    public AuthResponse register(RegisterRequest registerRequest) {
        if (userService.existsByEmail(registerRequest.getEmail())) {
            throw new BadRequestException("Email is already registered");
        }

        User newUser =
                userService.createUser(
                        registerRequest.getEmail(),
                        passwordEncoder.encode(registerRequest.getPassword()),
                        registerRequest.getUsername(),
                        registerRequest.getPhone());

        walletService.createDefaultWallet(newUser.getId());
        categoryService.createDefaultCategories(newUser.getId());

        String accessToken = jwtTokenProvider.generateToken(newUser.getEmail());
        return new AuthResponse(accessToken);
    }

    public AuthResponse login(LoginRequest loginRequest) {
        User user =
                userService
                        .findUserByEmail(loginRequest.getEmail())
                        .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        String accessToken = jwtTokenProvider.generateToken(user.getEmail());
        return new AuthResponse(accessToken);
    }
}
