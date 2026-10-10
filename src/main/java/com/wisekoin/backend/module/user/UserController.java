package com.wisekoin.backend.module.user;

import com.wisekoin.backend.module.auth.security.CustomUserDetails;
import com.wisekoin.backend.module.user.dto.BaseCurrencyUpdateRequest;
import com.wisekoin.backend.module.user.dto.ChangePasswordRequest;
import com.wisekoin.backend.module.user.dto.DeleteAccountRequest;
import com.wisekoin.backend.module.user.dto.UserResponse;
import com.wisekoin.backend.module.user.mapper.UserMapper;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    private final UserService userService;
    private final UserMapper userMapper;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(Principal principal) {
        User currentUser = userService.getUserByEmail(principal.getName());
        return ResponseEntity.ok(userMapper.toResponse(currentUser));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateUserProfile(
            @RequestBody User user, Principal principal) {
        User updatedUser = userService.updateUserProfile(principal.getName(), user);
        return ResponseEntity.ok(userMapper.toResponse(updatedUser));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        userService.changePassword(
                currentUser.getId(), request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/base-currency")
    public ResponseEntity<UserResponse> updateBaseCurrency(
            @Valid @RequestBody BaseCurrencyUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        User updatedUser =
                userService.updateBaseCurrency(currentUser.getId(), request.getBaseCurrency());
        return ResponseEntity.ok(userMapper.toResponse(updatedUser));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(
            @Valid @RequestBody DeleteAccountRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        userService.deleteAccount(currentUser.getId(), request.getCurrentPassword());
        return ResponseEntity.noContent().build();
    }
}
