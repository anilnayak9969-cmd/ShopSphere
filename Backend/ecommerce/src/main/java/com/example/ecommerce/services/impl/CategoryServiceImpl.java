package com.example.ecommerce.services.impl;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dtos.category.CategoryRequest;
import com.example.ecommerce.dtos.category.CategoryResponse;
import com.example.ecommerce.dtos.common.MessageResponse;
import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.entites.Category;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.CategoryRepository;
import com.example.ecommerce.repositories.ProductRepository;
import com.example.ecommerce.services.CategoryService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private static final int MAX_PAGE_SIZE = 50;

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getActiveCategories() {
        List<Category> categories = categoryRepository.findByStatusTrueOrderByCategoryNameAsc();
        Map<Integer, long[]> counts = productCounts(categories.stream().map(Category::getCategoryId).toList());
        return categories.stream().map(c -> toResponse(c, counts)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> searchCategories(String search, Boolean active, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("categoryName").ascending());
        Page<Category> result = categoryRepository.search(blankToNull(search), active, pageable);
        Map<Integer, long[]> counts = productCounts(result.getContent().stream().map(Category::getCategoryId).toList());
        return PageResponse.of(result, c -> toResponse(c, counts));
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategory(Integer categoryId) {
        Category category = findCategory(categoryId);
        return toResponse(category, productCounts(List.of(categoryId)));
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        String name = cleanName(request.categoryName());
        if (categoryRepository.existsByCategoryNameIgnoreCase(name)) {
            throw duplicateName(name);
        }
        Category category = new Category();
        apply(category, request, name);
        category.setParent(resolveParent(request.parentCategoryId(), null));
        category.setStatus(true);
        Category saved = categoryRepository.save(category);
        return CategoryResponse.from(saved, new long[6]);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Integer categoryId, CategoryRequest request) {
        Category category = findCategory(categoryId);
        String name = cleanName(request.categoryName());
        if (categoryRepository.existsByCategoryNameIgnoreCaseAndCategoryIdNot(name, categoryId)) {
            throw duplicateName(name);
        }
        apply(category, request, name);
        category.setParent(resolveParent(request.parentCategoryId(), categoryId));
        // saveAndFlush so updatedAt (set by @PreUpdate) is filled in before we build the response.
        return toResponse(categoryRepository.saveAndFlush(category), productCounts(List.of(categoryId)));
    }

    @Override
    @Transactional
    public CategoryResponse updateStatus(Integer categoryId, boolean active) {
        Category category = findCategory(categoryId);
        if (!active && category.isStatus()) {
            long activeProducts = productRepository.countByCategoryCategoryIdAndActiveTrue(categoryId);
            if (activeProducts > 0) {
                // This is the warning required by the assignment: products must be moved first.
                throw ApiException.conflict("CATEGORY_HAS_PRODUCTS",
                        "\"" + category.getCategoryName() + "\" still has " + activeProducts
                                + " active product(s). Assign them to another category before deactivating it.");
            }
            long activeChildren = categoryRepository.countByParentCategoryIdAndStatusTrue(categoryId);
            if (activeChildren > 0) {
                throw ApiException.conflict("CATEGORY_HAS_SUBCATEGORIES",
                        "\"" + category.getCategoryName() + "\" still has " + activeChildren
                                + " active subcategor" + (activeChildren == 1 ? "y" : "ies") + ". Deactivate or move them first.");
            }
        }
        if (active && category.getParent() != null && !category.getParent().isStatus()) {
            throw ApiException.badRequest("PARENT_INACTIVE",
                    "Activate the parent category \"" + category.getParent().getCategoryName() + "\" first.");
        }
        category.setStatus(active);
        return toResponse(categoryRepository.saveAndFlush(category), productCounts(List.of(categoryId)));
    }

    @Override
    @Transactional
    public CategoryResponse softDelete(Integer categoryId) {
        return updateStatus(categoryId, false);
    }

    @Override
    @Transactional
    public MessageResponse reassignProducts(Integer sourceCategoryId, Integer targetCategoryId) {
        if (sourceCategoryId.equals(targetCategoryId)) {
            throw ApiException.badRequest("SAME_CATEGORY", "Choose a different category to move the products into.");
        }
        Category source = findCategory(sourceCategoryId);
        Category target = findCategory(targetCategoryId);
        if (!target.isStatus()) {
            throw ApiException.badRequest("TARGET_INACTIVE", "Products can only be moved into an active category.");
        }
        int moved = productRepository.reassignCategory(source, target, LocalDateTime.now());
        return new MessageResponse(moved + " product(s) moved from \"" + source.getCategoryName() + "\" to \""
                + target.getCategoryName() + "\".");
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Integer> withDescendants(Integer categoryId) {
        return withDescendants(categoryId, childrenByParent());
    }

    // ---------------------------------------------------------------- helpers

    /** parentId -> its direct subcategory ids, built from one query. */
    private Map<Integer, List<Integer>> childrenByParent() {
        Map<Integer, List<Integer>> children = new HashMap<>();
        for (Object[] row : categoryRepository.parentLinks()) {
            if (row[1] != null) {
                children.computeIfAbsent((Integer) row[1], k -> new java.util.ArrayList<>()).add((Integer) row[0]);
            }
        }
        return children;
    }

    private static Set<Integer> withDescendants(Integer categoryId, Map<Integer, List<Integer>> children) {
        Set<Integer> result = new LinkedHashSet<>();
        Deque<Integer> queue = new ArrayDeque<>(List.of(categoryId));
        while (!queue.isEmpty()) {
            Integer id = queue.poll();
            if (result.add(id)) {
                queue.addAll(children.getOrDefault(id, List.of()));
            }
        }
        return result;
    }

    /**
     * Validates the requested parent: it must exist, be active, not be the category itself and not
     * be one of its descendants (which would create a loop like A > B > A).
     */
    private Category resolveParent(Integer parentId, Integer selfId) {
        if (parentId == null) {
            return null;
        }
        if (parentId.equals(selfId)) {
            throw ApiException.badRequest("INVALID_PARENT", "A category cannot be its own parent.");
        }
        Category parent = categoryRepository.findById(parentId)
                .orElseThrow(() -> ApiException.badRequest("INVALID_PARENT", "The selected parent category does not exist."));
        if (!parent.isStatus()) {
            throw ApiException.badRequest("INVALID_PARENT", "The selected parent category is inactive.");
        }
        if (selfId != null && withDescendants(selfId).contains(parentId)) {
            throw ApiException.badRequest("INVALID_PARENT",
                    "A category cannot be placed inside one of its own subcategories.");
        }
        return parent;
    }

    private Category findCategory(Integer categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> ApiException.notFound("Category " + categoryId + " was not found."));
    }

    private void apply(Category category, CategoryRequest request, String cleanName) {
        category.setCategoryName(cleanName);
        category.setDescription(request.description().trim());
        category.setImageUrl(blankToNull(request.imageUrl()));
    }

    /** Trims and collapses repeated spaces, so "Home   Decor " and "Home Decor" count as duplicates. */
    private static String cleanName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }

    private static ApiException duplicateName(String name) {
        return ApiException.conflict("DUPLICATE_CATEGORY", "A category named \"" + name + "\" already exists.");
    }

    /**
     * Product counts for the categories on the page with one GROUP BY query (covering their subcategories too):
     * categoryId -> [total, active, live, pending, totalWithSubcategories, liveWithSubcategories].
     */
    private Map<Integer, long[]> productCounts(Collection<Integer> categoryIds) {
        Map<Integer, long[]> counts = new HashMap<>();
        if (categoryIds.isEmpty()) {
            return counts;
        }
        Map<Integer, List<Integer>> children = childrenByParent();
        Map<Integer, Set<Integer>> subtree = new HashMap<>();
        Set<Integer> allIds = new LinkedHashSet<>();
        for (Integer id : categoryIds) {
            Set<Integer> ids = withDescendants(id, children);
            subtree.put(id, ids);
            allIds.addAll(ids);
        }
        Map<Integer, long[]> direct = new HashMap<>(); // [total, active, live, pending]
        for (Object[] row : productRepository.countByCategoryIds(allIds)) {
            long[] c = new long[4];
            for (int i = 0; i < 4; i++) {
                c[i] = row[i + 1] == null ? 0 : ((Number) row[i + 1]).longValue();
            }
            direct.put((Integer) row[0], c);
        }
        for (Integer id : categoryIds) {
            long[] own = direct.getOrDefault(id, new long[4]);
            long total = 0;
            long live = 0;
            for (Integer sub : subtree.get(id)) {
                long[] c = direct.getOrDefault(sub, new long[4]);
                total += c[0];
                live += c[2];
            }
            counts.put(id, new long[]{own[0], own[1], own[2], own[3], total, live});
        }
        return counts;
    }

    private static CategoryResponse toResponse(Category category, Map<Integer, long[]> counts) {
        return CategoryResponse.from(category, counts.getOrDefault(category.getCategoryId(), new long[6]));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
