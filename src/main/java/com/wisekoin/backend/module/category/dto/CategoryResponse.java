package com.wisekoin.backend.module.category.dto;

import com.wisekoin.backend.module.category.Category;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class CategoryResponse {

    private Long id;
    private String name;
    private Category.CategoryType type;
    private String icon;

}
