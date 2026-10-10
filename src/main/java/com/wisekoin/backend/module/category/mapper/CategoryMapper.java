package com.wisekoin.backend.module.category.mapper;

import com.wisekoin.backend.module.category.Category;
import com.wisekoin.backend.module.category.dto.CategoryResponse;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {
    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(), category.getName(), category.getType(), category.getIcon());
    }
}
