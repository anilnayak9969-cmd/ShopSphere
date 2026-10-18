package com.example.ecommerce.services;

import com.example.ecommerce.dtos.cart.AddToCartRequest;
import com.example.ecommerce.dtos.cart.CartResponse;

public interface CartService {

    CartResponse getCart(Long userId);

    CartResponse addItem(Long userId, AddToCartRequest request);

    CartResponse updateItem(Long userId, Long cartItemId, int quantity);

    CartResponse removeItem(Long userId, Long cartItemId);

    /** Moves a cart line to the wishlist ("Save for later"). */
    CartResponse saveForLater(Long userId, Long cartItemId);

    /** The customer has seen the updated prices; clears the "price changed" flags. */
    CartResponse acknowledgePrices(Long userId);
}
