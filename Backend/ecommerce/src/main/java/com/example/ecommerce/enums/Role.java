package com.example.ecommerce.enums;

/**
 * Security role of an account. Spring Security sees these as "ROLE_CUSTOMER", "ROLE_SELLER", ...
 * Seller approval is tracked separately in {@link SellerStatus} so that a seller's role never
 * changes when an admin approves or suspends their store.
 */
public enum Role {
    CUSTOMER,
    SELLER,
    ADMIN,
    DELIVERY_PARTNER
}
