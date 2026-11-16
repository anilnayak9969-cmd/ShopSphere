package com.example.ecommerce.dtos.wishlist;

import java.time.LocalDateTime;

import com.example.ecommerce.dtos.product.ProductResponse;
import com.example.ecommerce.entites.WishlistItem;

/**
 * available=false when the product was deactivated or its category/seller is no longer active.
 * availability: IN_STOCK, LOW_STOCK (5 or fewer), OUT_OF_STOCK or UNAVAILABLE, with a label for display.
 */
public record WishlistItemResponse(Long wishlistItemId, boolean available, String availability,
                                   String availabilityLabel, LocalDateTime addedAt, ProductResponse product) {

    public static WishlistItemResponse from(WishlistItem item, boolean available) {
        int stock = item.getProduct().getStockQuantity();
        String status = !available ? "UNAVAILABLE" : stock == 0 ? "OUT_OF_STOCK" : stock <= 5 ? "LOW_STOCK" : "IN_STOCK";
        String label = switch (status) {
            case "UNAVAILABLE" -> "No longer available";
            case "OUT_OF_STOCK" -> "Out of stock";
            case "LOW_STOCK" -> "Only " + stock + " left";
            default -> "In stock";
        };
        return new WishlistItemResponse(item.getWishlistItemId(), available, status, label, item.getCreatedAt(),
                ProductResponse.from(item.getProduct()));
    }
}
