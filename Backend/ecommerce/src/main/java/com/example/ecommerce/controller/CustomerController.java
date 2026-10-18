package com.example.ecommerce.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.dtos.cart.AddToCartRequest;
import com.example.ecommerce.dtos.cart.CartResponse;
import com.example.ecommerce.dtos.cart.UpdateCartItemRequest;
import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.order.CheckoutRequest;
import com.example.ecommerce.dtos.order.CheckoutResponse;
import com.example.ecommerce.dtos.order.OrderResponse;
import com.example.ecommerce.dtos.order.PaymentFailedRequest;
import com.example.ecommerce.dtos.order.PaymentResponse;
import com.example.ecommerce.dtos.order.VerifyPaymentRequest;
import com.example.ecommerce.dtos.returns.ReturnCreateRequest;
import com.example.ecommerce.dtos.returns.ReturnResponse;
import com.example.ecommerce.dtos.review.ReviewResponse;
import com.example.ecommerce.security.UserPrincipal;
import com.example.ecommerce.services.CartService;
import com.example.ecommerce.services.CheckoutService;
import com.example.ecommerce.services.OrderService;
import com.example.ecommerce.services.ReturnService;
import com.example.ecommerce.services.ReviewService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** CUSTOMER only: cart, checkout (Razorpay or Cash on Delivery), orders, payments, returns, reviews. */
@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CartService cartService;
    private final CheckoutService checkoutService;
    private final OrderService orderService;
    private final ReturnService returnService;
    private final ReviewService reviewService;

    // ------------------------------------------------------------------ cart

    @GetMapping("/cart")
    public CartResponse cart(@AuthenticationPrincipal UserPrincipal user) {
        return cartService.getCart(user.getId());
    }

    @PostMapping("/cart/items")
    public CartResponse addToCart(@AuthenticationPrincipal UserPrincipal user,
                                  @Valid @RequestBody AddToCartRequest request) {
        return cartService.addItem(user.getId(), request);
    }

    @PutMapping("/cart/items/{itemId}")
    public CartResponse updateCartItem(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long itemId,
                                       @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateItem(user.getId(), itemId, request.quantity());
    }

    @DeleteMapping("/cart/items/{itemId}")
    public CartResponse removeCartItem(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long itemId) {
        return cartService.removeItem(user.getId(), itemId);
    }

    @PostMapping("/cart/items/{itemId}/save-for-later")
    public CartResponse saveForLater(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long itemId) {
        return cartService.saveForLater(user.getId(), itemId);
    }

    @PostMapping("/cart/acknowledge-prices")
    public CartResponse acknowledgePrices(@AuthenticationPrincipal UserPrincipal user) {
        return cartService.acknowledgePrices(user.getId());
    }

    // -------------------------------------------------------------- checkout

    /** RAZORPAY: returns the Razorpay order to open Checkout. COD: places the order immediately. */
    @PostMapping("/checkout")
    public CheckoutResponse checkout(@AuthenticationPrincipal UserPrincipal user,
                                     @Valid @RequestBody CheckoutRequest request) {
        return checkoutService.createCheckout(user.getId(), request);
    }

    @PostMapping("/payments/verify")
    public OrderResponse verifyPayment(@AuthenticationPrincipal UserPrincipal user,
                                       @Valid @RequestBody VerifyPaymentRequest request) {
        return checkoutService.verifyPayment(user.getId(), request);
    }

    @PostMapping("/payments/failed")
    public ResponseEntity<Void> paymentFailed(@AuthenticationPrincipal UserPrincipal user,
                                              @Valid @RequestBody PaymentFailedRequest request) {
        checkoutService.recordPaymentFailure(user.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/payments")
    public PageResponse<PaymentResponse> payments(@AuthenticationPrincipal UserPrincipal user,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return orderService.customerPayments(user.getId(), page, size);
    }

    // ---------------------------------------------------------------- orders

    @GetMapping("/orders")
    public PageResponse<OrderResponse> orders(@AuthenticationPrincipal UserPrincipal user,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return orderService.customerOrders(user.getId(), page, size);
    }

    @GetMapping("/orders/{orderId}")
    public OrderResponse order(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long orderId) {
        return orderService.customerOrder(user.getId(), orderId);
    }

    @PostMapping("/orders/{orderId}/cancel")
    public OrderResponse cancel(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long orderId) {
        return orderService.cancelByCustomer(user.getId(), orderId);
    }

    // --------------------------------------------------------------- returns

    @PostMapping("/returns")
    @ResponseStatus(HttpStatus.CREATED)
    public ReturnResponse requestReturn(@AuthenticationPrincipal UserPrincipal user,
                                        @Valid @RequestBody ReturnCreateRequest request) {
        return returnService.requestReturn(user.getId(), request);
    }

    @GetMapping("/returns")
    public PageResponse<ReturnResponse> returns(@AuthenticationPrincipal UserPrincipal user,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return returnService.customerReturns(user.getId(), page, size);
    }

    // --------------------------------------------------------------- reviews

    @GetMapping("/reviews")
    public PageResponse<ReviewResponse> myReviews(@AuthenticationPrincipal UserPrincipal user,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return reviewService.customerReviews(user.getId(), page, size);
    }
}
