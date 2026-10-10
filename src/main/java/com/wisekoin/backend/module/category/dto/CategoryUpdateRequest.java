package com.wisekoin.backend.module.category.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wisekoin.backend.module.category.Category;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryUpdateRequest {
    private String name;
    private Category.CategoryType type;
    private String icon;
    @JsonIgnore
    private boolean iconSet;

    public void setIcon(String icon) {
        this.icon = icon;
        this.iconSet = true;
    }
}
