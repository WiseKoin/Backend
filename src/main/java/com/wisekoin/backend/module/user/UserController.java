package com.wisekoin.backend.module.user;

import com.wisekoin.backend.module.user.dto.UserResponse;
import com.wisekoin.backend.module.user.mapper.UserMapper;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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
}
