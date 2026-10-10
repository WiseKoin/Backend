package com.wisekoin.backend.module.category;

import com.wisekoin.backend.module.auth.security.CustomUserDetails;
import com.wisekoin.backend.module.category.mapper.CategoryMapper;
import com.wisekoin.backend.module.category.dto.CategoryRequest;
import com.wisekoin.backend.module.category.dto.CategoryResponse;
import com.wisekoin.backend.module.category.dto.CategoryUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
            @Valid @RequestBody CategoryRequest categoryRequest,
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

        List<CategoryResponse> categoryResponseList = categoryList.stream()
                .map(categoryMapper::toResponse)
                .toList();

        return ResponseEntity.ok(categoryResponseList);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ResponseEntity.ok(categoryMapper.toResponse(
                categoryService.updateCategory(currentUser.getId(), id, request)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CategoryResponse> patchCategory(
            @PathVariable Long id,
            @RequestBody CategoryUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        return ResponseEntity.ok(categoryMapper.toResponse(
                categoryService.patchCategory(currentUser.getId(), id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser
    ) {
        categoryService.deleteCategory(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }

}
