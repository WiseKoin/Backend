package com.wisekoin.backend.module.category;

import com.wisekoin.backend.module.auth.security.CustomUserDetails;
import com.wisekoin.backend.module.category.mapper.CategoryMapper;
import com.wisekoin.backend.module.category.dto.CategoryRequest;
import com.wisekoin.backend.module.category.dto.CategoryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Slf4j
public class CategoryController {

    private final CategoryService categoryService;
    private final CategoryMapper categoryMapper;

    @PostMapping
    public ResponseEntity<CategoryResponse> createCategory(
            @RequestBody CategoryRequest categoryRequest,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        Category newCategory = categoryService.createCategory(
                currentUser.getId(),
                categoryRequest.getName(),
                categoryRequest.getType(),
                categoryRequest.getIcon()
        );

        return ResponseEntity.ok(categoryMapper.toResponse(newCategory));
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> getCategoryByUserIdAndType(
            @RequestParam Category.CategoryType categoryType,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        List<Category> categoryList = categoryService.getCategoryByType(categoryType, currentUser.getId());

        List<CategoryResponse> categoryResponseList = new ArrayList<>();
        for (Category category : categoryList) {
            CategoryResponse response = categoryMapper.toResponse(category);
            categoryResponseList.add(response);
        }

        return ResponseEntity.ok(categoryResponseList);
    }

}
