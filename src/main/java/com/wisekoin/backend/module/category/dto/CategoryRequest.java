package com.wisekoin.backend.module.category.dto;

import com.wisekoin.backend.module.category.Category;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CategoryRequest {

    @NotBlank
    private String name;

    @NotBlank
    private Category.CategoryType type;

    private String icon;

}
