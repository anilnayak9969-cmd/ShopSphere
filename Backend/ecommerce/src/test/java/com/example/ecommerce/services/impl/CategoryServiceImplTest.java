package com.example.ecommerce.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.example.ecommerce.dtos.category.CategoryRequest;
import com.example.ecommerce.dtos.category.CategoryResponse;
import com.example.ecommerce.entites.Category;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.CategoryRepository;
import com.example.ecommerce.repositories.ProductRepository;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    @Test
    void createTrimsNameAndStartsActive() {
        when(categoryRepository.existsByCategoryNameIgnoreCase("Home Decor")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse response = categoryService.createCategory(
                new CategoryRequest("  Home   Decor ", "Lamps, rugs and cushions", "", null));

        assertThat(response.categoryName()).isEqualTo("Home Decor");
        assertThat(response.active()).isTrue();
        assertThat(response.imageUrl()).isNull();
        assertThat(response.productCount()).isZero();
    }

    @Test
    void createRejectsDuplicateNameIgnoringCase() {
        when(categoryRepository.existsByCategoryNameIgnoreCase("electronics")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(
                new CategoryRequest("electronics", "Phones and laptops", null, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(((ApiException) ex).getCode()).isEqualTo("DUPLICATE_CATEGORY");
                });
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateRejectsNameUsedByAnotherCategory() {
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category(1, "Books", true)));
        when(categoryRepository.existsByCategoryNameIgnoreCaseAndCategoryIdNot("Toys", 1)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.updateCategory(1, new CategoryRequest("Toys", "Fun things", null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void deactivationIsBlockedWhileActiveProductsRemain() {
        when(categoryRepository.findById(5)).thenReturn(Optional.of(category(5, "Audio", true)));
        when(productRepository.countByCategoryCategoryIdAndActiveTrue(5)).thenReturn(3L);

        assertThatThrownBy(() -> categoryService.softDelete(5))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("CATEGORY_HAS_PRODUCTS"))
                .hasMessageContaining("3 active product(s)");
        verify(categoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void deactivationSucceedsWhenNoActiveProducts() {
        Category audio = category(5, "Audio", true);
        when(categoryRepository.findById(5)).thenReturn(Optional.of(audio));
        when(productRepository.countByCategoryCategoryIdAndActiveTrue(5)).thenReturn(0L);
        when(categoryRepository.saveAndFlush(audio)).thenReturn(audio);
        when(productRepository.countByCategoryIds(List.of(5))).thenReturn(List.<Object[]>of(new Object[]{5, 2L, 0L}));

        CategoryResponse response = categoryService.softDelete(5);

        assertThat(response.active()).isFalse();
        assertThat(response.productCount()).isEqualTo(2);
    }

    @Test
    void reassignIntoSameCategoryIsRejected() {
        assertThatThrownBy(() -> categoryService.reassignProducts(4, 4))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("different category");
    }

    @Test
    void reassignIntoInactiveCategoryIsRejected() {
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category(1, "Old", true)));
        when(categoryRepository.findById(2)).thenReturn(Optional.of(category(2, "Archived", false)));

        assertThatThrownBy(() -> categoryService.reassignProducts(1, 2))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("active category");
        verify(productRepository, never()).reassignCategory(any(), any(), any());
    }

    @Test
    void missingCategoryReturnsNotFound() {
        when(categoryRepository.findById(anyInt())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategory(99))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void duplicateCheckUsesCleanedName() {
        when(categoryRepository.existsByCategoryNameIgnoreCase(anyString())).thenReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(new CategoryRequest(" Kids  Wear", "Clothes", null, null)))
                .isInstanceOf(ApiException.class);
        verify(categoryRepository).existsByCategoryNameIgnoreCase("Kids Wear");
    }

    @Test
    void categoryCannotBeMovedInsideItsOwnSubcategory() {
        // Electronics(1) > Audio(2). Making Electronics a child of Audio would create a loop.
        Category electronics = category(1, "Electronics", true);
        Category audio = category(2, "Audio", true);
        when(categoryRepository.findById(1)).thenReturn(Optional.of(electronics));
        when(categoryRepository.findById(2)).thenReturn(Optional.of(audio));
        when(categoryRepository.parentLinks()).thenReturn(List.<Object[]>of(new Object[]{1, null}, new Object[]{2, 1}));

        assertThatThrownBy(() -> categoryService.updateCategory(1,
                new CategoryRequest("Electronics", "Phones and laptops", null, 2)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("INVALID_PARENT"));
    }

    @Test
    void categoryCannotBeItsOwnParent() {
        when(categoryRepository.findById(3)).thenReturn(Optional.of(category(3, "Books", true)));

        assertThatThrownBy(() -> categoryService.updateCategory(3, new CategoryRequest("Books", "Reading", null, 3)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("own parent");
    }

    @Test
    void deactivationIsBlockedWhileActiveSubcategoriesRemain() {
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category(1, "Electronics", true)));
        when(productRepository.countByCategoryCategoryIdAndActiveTrue(1)).thenReturn(0L);
        when(categoryRepository.countByParentCategoryIdAndStatusTrue(1)).thenReturn(2L);

        assertThatThrownBy(() -> categoryService.softDelete(1))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("CATEGORY_HAS_SUBCATEGORIES"));
    }

    @Test
    void descendantsIncludeEveryLevel() {
        when(categoryRepository.parentLinks()).thenReturn(List.<Object[]>of(
                new Object[]{1, null}, new Object[]{2, 1}, new Object[]{3, 2}, new Object[]{4, null}));

        assertThat(categoryService.withDescendants(1)).containsExactlyInAnyOrder(1, 2, 3);
    }

    private static Category category(int id, String name, boolean active) {
        Category c = new Category();
        c.setCategoryId(id);
        c.setCategoryName(name);
        c.setDescription(name + " description");
        c.setStatus(active);
        return c;
    }
}
