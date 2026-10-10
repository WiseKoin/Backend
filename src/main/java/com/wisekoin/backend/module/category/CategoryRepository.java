package com.wisekoin.backend.module.category;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByUserId(Long userId);

    List<Category> findAllByTypeAndUserId(Category.CategoryType type, Long userId);

    Optional<Category> findByNameAndTypeAndUserId(
            String name, Category.CategoryType type, Long userId);

    boolean existsByTypeAndUserId(Category.CategoryType type, Long userId);

    @Modifying
    @Query("delete from Category category where category.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
