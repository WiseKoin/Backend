package com.wisekoin.backend.module.user;

import com.wisekoin.backend.module.category.CategoryService;
import jakarta.transaction.Transactional;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final CategoryService categoryService;

    public User getUserByEmail(String email) {
        User user =
                userRepository
                        .findByEmail(email)
                        .orElseThrow(() -> new RuntimeException("User not found"));
        return user;
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
}
