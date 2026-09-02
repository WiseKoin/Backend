package com.wisekoin.backend.module.user;

import com.wisekoin.backend.module.user.dto.UserResponse;
import com.wisekoin.backend.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

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
    public ResponseEntity<UserResponse> updateUserProfile(Principal principal, @RequestBody User user) {
        User updatedUser = userService.updateUserProfile(principal.getName(), user);
        return ResponseEntity.ok(userMapper.toResponse(updatedUser));
    }

}
