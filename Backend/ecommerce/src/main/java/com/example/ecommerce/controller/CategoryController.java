package com.example.ecommerce.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.dtos.category.CategoryResponse;
import com.example.ecommerce.services.CategoryService;

import lombok.RequiredArgsConstructor;

/** Public: anyone (even logged out) can list the active categories. */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public List<CategoryResponse> activeCategories() {
        return categoryService.getActiveCategories();
    }
}
