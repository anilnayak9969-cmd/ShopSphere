package com.example.ecommerce.dtos.wishlist;

import java.time.LocalDateTime;

import com.example.ecommerce.dtos.product.ProductResponse;
import com.example.ecommerce.entites.WishlistItem;

/** available=false when the product was deactivated or its category/seller is no longer active. */
public record WishlistItemResponse(Long wishlistItemId, boolean available, LocalDateTime addedAt, ProductResponse product) {

    public static WishlistItemResponse from(WishlistItem item, boolean available) {
        return new WishlistItemResponse(item.getWishlistItemId(), available, item.getCreatedAt(),
                ProductResponse.from(item.getProduct()));
    }
}
