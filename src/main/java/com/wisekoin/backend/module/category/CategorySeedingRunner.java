package com.wisekoin.backend.module.category;

import com.wisekoin.backend.module.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class CategorySeedingRunner implements CommandLineRunner {

    private final CategoryService categoryService;
    private final UserRepository userRepository;

    @Override
    public void run(String... args) {
        log.info("Seeding 'Lend' and 'Borrow' categories for all existing users...");
        userRepository.findAll().forEach(user -> {
            try {
                categoryService.seedInitialCategories(user.getId());
                log.info("Seeded categories for user id={}", user.getId());
            } catch (Exception e) {
                log.warn("Failed to seed categories for user id={}: {}", user.getId(), e.getMessage());
            }
        });
        log.info("Category seeding complete.");
    }
}
