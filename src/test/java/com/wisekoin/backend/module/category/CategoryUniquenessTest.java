package com.wisekoin.backend.module.category;

import static org.assertj.core.api.Assertions.assertThat;

import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class CategoryUniquenessTest {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void allowsDifferentUsersToHaveCategoriesWithTheSameName() {
        User firstUser = userRepository.saveAndFlush(user("first@example.com"));
        User secondUser = userRepository.saveAndFlush(user("second@example.com"));

        categoryRepository.saveAndFlush(category(firstUser, "Dining"));
        categoryRepository.saveAndFlush(category(secondUser, "Dining"));

        assertThat(categoryRepository.findAll())
                .extracting(category -> category.getUser().getId(), Category::getName)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(firstUser.getId(), "Dining"),
                        org.assertj.core.groups.Tuple.tuple(secondUser.getId(), "Dining"));
    }

    private User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("encoded-password");
        return user;
    }

    private Category category(User user, String name) {
        Category category = new Category();
        category.setUser(user);
        category.setName(name);
        category.setType(Category.CategoryType.EXPENSE);
        return category;
    }
}
