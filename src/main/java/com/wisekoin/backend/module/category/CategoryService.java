package com.wisekoin.backend.module.category;

import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public Category createCategory(Long userId, String name, Category.CategoryType type, String icon) {
        Category newCategory = new Category();

        User userProxy = userRepository.getReferenceById(userId);

        newCategory.setUser(userProxy);
        newCategory.setName(name);
        newCategory.setType(type);
        newCategory.setIcon(icon);

        return categoryRepository.save(newCategory);
    }

    public void createDefaultCategories(Long userId) {
        User userProxy = userRepository.getReferenceById(userId);

        List<Category> userCategories = categoryRepository.findAllByUserId(userId);
        Set<String> existingNames = userCategories.stream()
                .map(c -> c.getName() != null ? c.getName().trim().toLowerCase() : "")
                .collect(Collectors.toSet());

        List<Category> defaults = List.of(
                createCategoryEntity(userProxy, "Dining", Category.CategoryType.EXPENSE, "dining"),
                createCategoryEntity(userProxy, "Coffee", Category.CategoryType.EXPENSE, "coffee"),
                createCategoryEntity(userProxy, "Transport", Category.CategoryType.EXPENSE, "transport"),
                createCategoryEntity(userProxy, "Shopping", Category.CategoryType.EXPENSE, "shopping"),
                createCategoryEntity(userProxy, "Entertainment", Category.CategoryType.EXPENSE, "sports_esports"),
                createCategoryEntity(userProxy, "Education", Category.CategoryType.EXPENSE, "education"),
                createCategoryEntity(userProxy, "Salary", Category.CategoryType.INCOME, "salary"),
                createCategoryEntity(userProxy, "Bonus", Category.CategoryType.INCOME, "income"),
                createCategoryEntity(userProxy, "Other Income", Category.CategoryType.INCOME, "money"),
                createCategoryEntity(userProxy, "Lend", Category.CategoryType.LEND, "lend"),
                createCategoryEntity(userProxy, "Borrow", Category.CategoryType.BORROW, "borrow")
        );

        List<Category> toSave = defaults.stream()
                .filter(c -> !existingNames.contains(c.getName().trim().toLowerCase()))
                .toList();

        if (!toSave.isEmpty()) {
            categoryRepository.saveAll(toSave);
        }
    }

    public List<Category> getAllCategories(Long userId) {
        List<Category> existing = categoryRepository.findAllByUserId(userId);
        if (existing.isEmpty()) {
            createDefaultCategories(userId);
            return categoryRepository.findAllByUserId(userId);
        }
        return existing;
    }

    private Category createCategoryEntity(User userProxy, String name, Category.CategoryType type, String icon) {
        Category category = new Category();
        category.setUser(userProxy);
        category.setName(name);
        category.setType(type);
        category.setIcon(icon);
        return category;
    }

    public List<Category> getCategoryByType(Category.CategoryType type, Long userId) {
        List<Category> allUserCategories = categoryRepository.findAllByUserId(userId);
        if (allUserCategories.isEmpty()) {
            createDefaultCategories(userId);
        }
        return categoryRepository.findAllByTypeAndUserId(type, userId);
    }
}
