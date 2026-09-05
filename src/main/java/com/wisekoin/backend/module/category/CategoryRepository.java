package com.wisekoin.backend.module.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByTypeAndUserId(Category.CategoryType type, Long userId);

    List<Category> findAllByUserId(Long userId);
    Optional<Category> findByNameAndTypeAndUserId(String name, Category.CategoryType type, Long userId);

    boolean existsByTypeAndUserId(Category.CategoryType type, Long userId);

    List<Category> findByTypeAndUserId(Category.CategoryType type, Long userId);

}
