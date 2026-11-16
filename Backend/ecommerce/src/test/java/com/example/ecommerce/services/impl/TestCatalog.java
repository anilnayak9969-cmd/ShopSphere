package com.example.ecommerce.services.impl;

import java.math.BigDecimal;

import com.example.ecommerce.entites.Category;
import com.example.ecommerce.entites.Product;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.ApprovalStatus;
import com.example.ecommerce.enums.Role;
import com.example.ecommerce.enums.SellerStatus;

/** Small builders shared by the cart and wishlist tests. */
final class TestCatalog {

    private TestCatalog() {
    }

    static User customer(Long id, String name) {
        User u = new User();
        u.setId(id);
        u.setFullName(name);
        u.setEmail(name.toLowerCase().replace(' ', '.') + "@example.com");
        u.setRole(Role.CUSTOMER);
        u.setActive(true);
        u.setEmailVerified(true);
        return u;
    }

    /** A product that is on sale (approved, active, active category, approved seller). */
    static Product product(Long id, String name, String sellingPrice, int stock) {
        Category category = new Category();
        category.setCategoryId(1);
        category.setCategoryName("Home Décor");
        category.setStatus(true);
        User seller = new User();
        seller.setId(99L);
        seller.setRole(Role.SELLER);
        seller.setSellerStatus(SellerStatus.APPROVED);
        seller.setActive(true);
        seller.setStoreName("UrbanNest Home");
        Product p = new Product();
        p.setProductId(id);
        p.setProductName(name);
        p.setSku("SKU-" + id);
        p.applyPricing(new BigDecimal(sellingPrice), 0);
        p.setStockQuantity(stock);
        p.setCategory(category);
        p.setSeller(seller);
        p.setActive(true);
        p.setApprovalStatus(ApprovalStatus.APPROVED);
        return p;
    }
}
