package com.wisekoin.backend.module.user;

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

    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser(Principal principal) {
        User user = userService.getUserByEmail(principal.getName());
        return ResponseEntity.ok(user);
    }

    @PutMapping("/me")
    public ResponseEntity<User> updateUserProfile(Principal principal, @RequestBody User user) {
        User updatedUser = userService.updateUserProfile(principal.getName(), user);
        return ResponseEntity.ok(updatedUser);
    }

}
