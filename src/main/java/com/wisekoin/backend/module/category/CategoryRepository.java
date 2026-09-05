package com.wisekoin.backend.module.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByTypeAndUserId(Category.CategoryType type, Long userId);

    List<Category> findAllByUserId(Long userId);

}
