package com.example.ecommerce.services.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dtos.wishlist.WishlistItemResponse;
import com.example.ecommerce.entites.Product;
import com.example.ecommerce.entites.WishlistItem;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.ProductRepository;
import com.example.ecommerce.repositories.UserRepository;
import com.example.ecommerce.repositories.WishlistItemRepository;
import com.example.ecommerce.services.WishlistService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WishlistServiceImpl implements WishlistService {

    private static final int MAX_ITEMS = 100;

    private final WishlistItemRepository wishlistRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<WishlistItemResponse> getWishlist(Long userId) {
        return wishlistRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(item -> WishlistItemResponse.from(item, ProductServiceImpl.isPubliclyVisible(item.getProduct())))
                .toList();
    }

    @Override
    @Transactional
    public List<WishlistItemResponse> add(Long userId, Long productId) {
        if (!wishlistRepository.existsByUserIdAndProductProductId(userId, productId)) {
            Product product = productRepository.findWithDetailsByProductId(productId)
                    .filter(ProductServiceImpl::isPubliclyVisible)
                    .orElseThrow(() -> ApiException.notFound("This product is not available."));
            if (wishlistRepository.countByUserId(userId) >= MAX_ITEMS) {
                throw ApiException.badRequest("WISHLIST_FULL", "Your wishlist can hold up to " + MAX_ITEMS + " items.");
            }
            WishlistItem item = new WishlistItem();
            item.setUser(userRepository.getReferenceById(userId));
            item.setProduct(product);
            wishlistRepository.save(item);
        }
        return getWishlist(userId);
    }

    @Override
    @Transactional
    public List<WishlistItemResponse> remove(Long userId, Long productId) {
        wishlistRepository.findByUserIdAndProductProductId(userId, productId).ifPresent(wishlistRepository::delete);
        return getWishlist(userId);
    }
}
