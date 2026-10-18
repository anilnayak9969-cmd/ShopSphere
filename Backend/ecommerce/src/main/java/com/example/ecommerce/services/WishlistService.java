package com.example.ecommerce.services;

import java.util.List;

import com.example.ecommerce.dtos.wishlist.WishlistItemResponse;

public interface WishlistService {

    List<WishlistItemResponse> getWishlist(Long userId);

    /** Adding a product that is already saved is not an error — the list is returned unchanged. */
    List<WishlistItemResponse> add(Long userId, Long productId);

    List<WishlistItemResponse> remove(Long userId, Long productId);
}
