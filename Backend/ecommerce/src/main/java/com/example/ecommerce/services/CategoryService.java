package com.example.ecommerce.services;

import java.util.List;
import java.util.Set;

import com.example.ecommerce.dtos.category.CategoryRequest;
import com.example.ecommerce.dtos.category.CategoryResponse;
import com.example.ecommerce.dtos.common.MessageResponse;
import com.example.ecommerce.dtos.common.PageResponse;

public interface CategoryService {

    /** Storefront: active categories only. */
    List<CategoryResponse> getActiveCategories();

    /** Admin dashboard: search, filter by status, paginate. */
    PageResponse<CategoryResponse> searchCategories(String search, Boolean active, int page, int size);

    CategoryResponse getCategory(Integer categoryId);

    CategoryResponse createCategory(CategoryRequest request);

    CategoryResponse updateCategory(Integer categoryId, CategoryRequest request);

    /** Activate or deactivate. Deactivation is refused while active products or subcategories remain. */
    CategoryResponse updateStatus(Integer categoryId, boolean active);

    /** Soft delete = deactivate. The row stays so old orders and products keep their category. */
    CategoryResponse softDelete(Integer categoryId);

    /** Moves every product of one category into another, so the first can be deactivated. */
    MessageResponse reassignProducts(Integer sourceCategoryId, Integer targetCategoryId);

    /** The category plus all of its subcategories (any depth) — used to filter "Electronics" including "Mobiles". */
    Set<Integer> withDescendants(Integer categoryId);
}
