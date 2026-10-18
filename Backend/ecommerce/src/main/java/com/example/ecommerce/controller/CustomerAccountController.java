package com.example.ecommerce.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.dtos.account.AddressRequest;
import com.example.ecommerce.dtos.account.AddressResponse;
import com.example.ecommerce.dtos.coupon.ApplyCouponRequest;
import com.example.ecommerce.dtos.coupon.CouponQuote;
import com.example.ecommerce.dtos.review.ReviewEligibility;
import com.example.ecommerce.dtos.review.ReviewRequest;
import com.example.ecommerce.dtos.review.ReviewResponse;
import com.example.ecommerce.dtos.wishlist.WishlistItemResponse;
import com.example.ecommerce.security.UserPrincipal;
import com.example.ecommerce.services.AddressService;
import com.example.ecommerce.services.CouponService;
import com.example.ecommerce.services.ReviewService;
import com.example.ecommerce.services.WishlistService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** CUSTOMER only: wishlist, address book, reviews and coupons. */
@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class CustomerAccountController {

    private final WishlistService wishlistService;
    private final AddressService addressService;
    private final ReviewService reviewService;
    private final CouponService couponService;

    // -------------------------------------------------------------- wishlist

    @GetMapping("/wishlist")
    public List<WishlistItemResponse> wishlist(@AuthenticationPrincipal UserPrincipal user) {
        return wishlistService.getWishlist(user.getId());
    }

    @PostMapping("/wishlist/{productId}")
    public List<WishlistItemResponse> addToWishlist(@AuthenticationPrincipal UserPrincipal user,
                                                    @PathVariable Long productId) {
        return wishlistService.add(user.getId(), productId);
    }

    @DeleteMapping("/wishlist/{productId}")
    public List<WishlistItemResponse> removeFromWishlist(@AuthenticationPrincipal UserPrincipal user,
                                                         @PathVariable Long productId) {
        return wishlistService.remove(user.getId(), productId);
    }

    // ------------------------------------------------------------- addresses

    @GetMapping("/addresses")
    public List<AddressResponse> addresses(@AuthenticationPrincipal UserPrincipal user) {
        return addressService.list(user.getId());
    }

    @PostMapping("/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse createAddress(@AuthenticationPrincipal UserPrincipal user,
                                         @Valid @RequestBody AddressRequest request) {
        return addressService.create(user.getId(), request);
    }

    @PutMapping("/addresses/{id}")
    public AddressResponse updateAddress(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long id,
                                         @Valid @RequestBody AddressRequest request) {
        return addressService.update(user.getId(), id, request);
    }

    @PatchMapping("/addresses/{id}/default")
    public AddressResponse makeDefault(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long id) {
        return addressService.makeDefault(user.getId(), id);
    }

    @DeleteMapping("/addresses/{id}")
    public ResponseEntity<Void> deleteAddress(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long id) {
        addressService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    // --------------------------------------------------------------- reviews

    @GetMapping("/products/{productId}/review")
    public ReviewEligibility reviewEligibility(@AuthenticationPrincipal UserPrincipal user,
                                               @PathVariable Long productId) {
        return reviewService.eligibility(user.getId(), productId);
    }

    /** Creates or updates the customer's own review (one per product). */
    @PutMapping("/products/{productId}/review")
    public ReviewResponse saveReview(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long productId,
                                     @Valid @RequestBody ReviewRequest request) {
        return reviewService.saveReview(user.getId(), productId, request);
    }

    @DeleteMapping("/products/{productId}/review")
    public ResponseEntity<Void> deleteReview(@AuthenticationPrincipal UserPrincipal user,
                                             @PathVariable Long productId) {
        reviewService.deleteOwnReview(user.getId(), productId);
        return ResponseEntity.noContent().build();
    }

    // --------------------------------------------------------------- coupons

    /** Previews the discount on the current cart. Checkout re-validates the code again. */
    @PostMapping("/coupons/apply")
    public CouponQuote applyCoupon(@AuthenticationPrincipal UserPrincipal user,
                                   @Valid @RequestBody ApplyCouponRequest request) {
        return couponService.quote(user.getId(), request.code());
    }
}
