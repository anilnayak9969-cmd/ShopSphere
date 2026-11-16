package com.example.ecommerce.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.example.ecommerce.dtos.product.AdminProductRequest;
import com.example.ecommerce.dtos.product.ProductRequest;
import com.example.ecommerce.dtos.product.ProductResponse;
import com.example.ecommerce.entites.Category;
import com.example.ecommerce.entites.Product;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.ApprovalStatus;
import com.example.ecommerce.enums.SellerStatus;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.CategoryRepository;
import com.example.ecommerce.repositories.ProductRepository;
import com.example.ecommerce.repositories.UserRepository;
import com.example.ecommerce.services.CategoryService;
import com.example.ecommerce.services.NotificationService;
import com.example.ecommerce.services.SettingsService;

/** Product Management module: admin add, update and deactivate rules. */
@ExtendWith(MockitoExtension.class)
class ProductServiceImplAdminTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private CategoryService categoryService;
    @Mock private NotificationService notificationService;
    @Mock private SettingsService settingsService;

    @InjectMocks
    private ProductServiceImpl productService;

    @Test
    void adminCreatedProductIsApprovedAndActiveStraightAway() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(seller(7L, SellerStatus.APPROVED)));
        when(categoryRepository.findById(3)).thenReturn(Optional.of(category(3, true)));
        when(productRepository.existsBySkuIgnoreCase("DESK-LAMP-01")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse saved = productService.adminCreateProduct(new AdminProductRequest(7L, request("desk-lamp-01", 3, 40)));

        assertThat(saved.sku()).isEqualTo("DESK-LAMP-01");
        assertThat(saved.approvalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(saved.active()).isTrue();
        assertThat(saved.stockQuantity()).isEqualTo(40);
        assertThat(saved.sellingPrice()).isEqualByComparingTo("1799.10");
        verify(notificationService).notify(any(User.class), anyString(), anyString(), anyString());
    }

    @Test
    void adminCannotAddProductToUnapprovedStore() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(seller(7L, SellerStatus.PENDING)));

        assertThatThrownBy(() -> productService.adminCreateProduct(new AdminProductRequest(7L, request("SKU-1", 3, 5))))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("SELLER_NOT_APPROVED"));
        verify(productRepository, never()).save(any());
    }

    @Test
    void adminCreateRejectsDuplicateSku() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(seller(7L, SellerStatus.APPROVED)));
        when(categoryRepository.findById(3)).thenReturn(Optional.of(category(3, true)));
        when(productRepository.existsBySkuIgnoreCase("SKU-1")).thenReturn(true);

        assertThatThrownBy(() -> productService.adminCreateProduct(new AdminProductRequest(7L, request("sku-1", 3, 5))))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void adminUpdateChangesPriceStockAndCategoryWithoutResettingApproval() {
        Product product = product(10L, category(3, true));
        when(productRepository.findWithDetailsByProductId(10L)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(4)).thenReturn(Optional.of(category(4, true)));
        when(productRepository.existsBySkuIgnoreCaseAndProductIdNot("DESK-LAMP-01", 10L)).thenReturn(false);
        when(productRepository.saveAndFlush(product)).thenReturn(product);

        ProductResponse updated = productService.adminUpdateProduct(10L, request("DESK-LAMP-01", 4, 3));

        assertThat(updated.categoryId()).isEqualTo(4);
        assertThat(updated.stockQuantity()).isEqualTo(3);
        assertThat(updated.price()).isEqualByComparingTo("1999.00");
        assertThat(updated.approvalStatus()).isEqualTo(ApprovalStatus.APPROVED);
    }

    @Test
    void adminUpdateRejectsInactiveNewCategory() {
        when(productRepository.findWithDetailsByProductId(10L)).thenReturn(Optional.of(product(10L, category(3, true))));
        when(categoryRepository.findById(9)).thenReturn(Optional.of(category(9, false)));

        assertThatThrownBy(() -> productService.adminUpdateProduct(10L, request("DESK-LAMP-01", 9, 3)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    void deactivateIsASoftDelete() {
        Product product = product(10L, category(3, true));
        when(productRepository.findWithDetailsByProductId(10L)).thenReturn(Optional.of(product));
        when(productRepository.saveAndFlush(product)).thenReturn(product);

        ProductResponse response = productService.adminSetActive(10L, false);

        assertThat(response.active()).isFalse();
        verify(productRepository, never()).delete(any(Product.class));
        verify(productRepository, never()).deleteById(eq(10L));
    }

    @Test
    void reactivationNeedsAnActiveCategory() {
        Product product = product(10L, category(3, false));
        product.setActive(false);
        when(productRepository.findWithDetailsByProductId(10L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.adminSetActive(10L, true))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("CATEGORY_INACTIVE"));
    }

    // ---------------------------------------------------------------- helpers

    private static ProductRequest request(String sku, int categoryId, int stock) {
        return new ProductRequest("Brass Desk Lamp", "Warm brass desk lamp with a fabric shade.", sku, "Lumen",
                new BigDecimal("1999"), 10, stock, null, categoryId, List.of(), List.of(), new BigDecimal("1.20"));
    }

    private static User seller(Long id, SellerStatus status) {
        User user = new User();
        user.setId(id);
        user.setSellerStatus(status);
        user.setActive(true);
        user.setStoreName("Lumen Home");
        return user;
    }

    private static Category category(int id, boolean active) {
        Category category = new Category();
        category.setCategoryId(id);
        category.setCategoryName("Category " + id);
        category.setStatus(active);
        return category;
    }

    private static Product product(Long id, Category category) {
        Product product = new Product();
        product.setProductId(id);
        product.setProductName("Brass Desk Lamp");
        product.setDescription("Warm brass desk lamp with a fabric shade.");
        product.setSku("DESK-LAMP-01");
        product.applyPricing(new BigDecimal("1499"), 0);
        product.setStockQuantity(12);
        product.setCategory(category);
        product.setSeller(seller(7L, SellerStatus.APPROVED));
        product.setApprovalStatus(ApprovalStatus.APPROVED);
        product.setActive(true);
        return product;
    }
}
