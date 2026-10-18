package com.example.ecommerce.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.dtos.category.CategoryRequest;
import com.example.ecommerce.dtos.category.CategoryResponse;
import com.example.ecommerce.dtos.category.ReassignProductsRequest;
import com.example.ecommerce.dtos.common.MessageResponse;
import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.common.StatusUpdateRequest;
import com.example.ecommerce.services.CategoryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Category Management module — ADMIN only (enforced in SecurityConfig for /api/admin/**). */
@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    /** GET /api/admin/categories?search=shoe&active=true&page=0&size=10 */
    @GetMapping
    public PageResponse<CategoryResponse> list(@RequestParam(required = false) String search,
                                               @RequestParam(required = false) Boolean active,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return categoryService.searchCategories(search, active, page, size);
    }

    @GetMapping("/{id}")
    public CategoryResponse get(@PathVariable Integer id) {
        return categoryService.getCategory(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
        return categoryService.createCategory(request);
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable Integer id, @Valid @RequestBody CategoryRequest request) {
        return categoryService.updateCategory(id, request);
    }

    /** Body: {"active": false} to deactivate, {"active": true} to restore. */
    @PatchMapping("/{id}/status")
    public CategoryResponse updateStatus(@PathVariable Integer id, @Valid @RequestBody StatusUpdateRequest request) {
        return categoryService.updateStatus(id, request.active());
    }

    /** Soft delete: sets status=false. Returns 409 if active products still use this category. */
    @DeleteMapping("/{id}")
    public CategoryResponse delete(@PathVariable Integer id) {
        return categoryService.softDelete(id);
    }

    @PostMapping("/{id}/reassign-products")
    public MessageResponse reassign(@PathVariable Integer id, @Valid @RequestBody ReassignProductsRequest request) {
        return categoryService.reassignProducts(id, request.targetCategoryId());
    }
}
