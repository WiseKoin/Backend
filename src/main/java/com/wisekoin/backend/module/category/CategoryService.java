package com.wisekoin.backend.module.category;

import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public Category createCategory(Long userId, String name, Category.CategoryType type, String icon) {
        Category newCategory = new Category();

        // getReferenceById() does not need to query SELECT as findByID()
        User userProxy = userRepository.getReferenceById(userId);

        newCategory.setUser(userProxy);
        newCategory.setName(name);
        newCategory.setType(type);
        newCategory.setIcon(icon);

        return categoryRepository.save(newCategory);
    }

    public List<Category> getCategoryByType(Category.CategoryType type, Long userId) {
        return categoryRepository.findAllByTypeAndUserId(type, userId);
    }

}
