package com.example.ecommerce.services.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dtos.cart.AdminCartDetailResponse;
import com.example.ecommerce.dtos.cart.AdminCartResponse;
import com.example.ecommerce.dtos.cart.CartItemResponse;
import com.example.ecommerce.dtos.cart.CartResponse;
import com.example.ecommerce.dtos.cart.CartSummaryResponse;
import com.example.ecommerce.dtos.cart.CartSummaryResponse.AbandonedProduct;
import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.entites.Cart;
import com.example.ecommerce.entites.CartItem;
import com.example.ecommerce.entites.PlatformSettings;
import com.example.ecommerce.entites.Product;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.CartRepository;
import com.example.ecommerce.repositories.OrderRepository;
import com.example.ecommerce.services.CartAdminService;
import com.example.ecommerce.services.NotificationService;
import com.example.ecommerce.services.PricingPolicy;
import com.example.ecommerce.services.SettingsService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CartAdminServiceImpl implements CartAdminService {

    public static final String ACTIVE = "ACTIVE";
    public static final String ABANDONED = "ABANDONED";
    public static final String EMPTY = "EMPTY";

    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final SettingsService settingsService;
    private final NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminCartResponse> list(String state, String search, int abandonedAfterHours, int page, int size) {
        int hours = clampHours(abandonedAfterHours);
        String filter = state == null || state.isBlank() ? null : state.trim().toUpperCase();
        if (filter != null && !List.of(ACTIVE, ABANDONED, EMPTY).contains(filter)) {
            throw ApiException.badRequest("INVALID_STATE", "State must be ACTIVE, ABANDONED or EMPTY.");
        }
        String term = search == null || search.isBlank() ? null : search.trim();
        PlatformSettings settings = settingsService.get();
        LocalDateTime now = LocalDateTime.now();
        return PageResponse.of(cartRepository.adminSearch(filter, now.minusHours(hours), term,
                        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50))),
                cart -> summarise(cart, CartServiceImpl.toResponse(cart, settings), hours, now));
    }

    @Override
    @Transactional(readOnly = true)
    public CartSummaryResponse summary(int abandonedAfterHours) {
        int hours = clampHours(abandonedAfterHours);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusHours(hours);
        PlatformSettings settings = settingsService.get();

        long active = 0;
        long abandoned = 0;
        BigDecimal activeValue = BigDecimal.ZERO;
        BigDecimal abandonedValue = BigDecimal.ZERO;
        Map<Long, long[]> units = new LinkedHashMap<>();      // productId -> [carts, units]
        Map<Long, BigDecimal> values = new LinkedHashMap<>();
        Map<Long, Product> products = new LinkedHashMap<>();

        for (Cart cart : cartRepository.findNonEmptyCustomerCarts()) {
            BigDecimal live = CartServiceImpl.toResponse(cart, settings).subtotal();
            if (isAbandoned(cart, cutoff)) {
                abandoned++;
                abandonedValue = abandonedValue.add(live);
                for (CartItem item : cart.getItems()) {
                    Product p = item.getProduct();
                    products.putIfAbsent(p.getProductId(), p);
                    long[] counts = units.computeIfAbsent(p.getProductId(), k -> new long[2]);
                    counts[0]++;
                    counts[1] += item.getQuantity();
                    values.merge(p.getProductId(), PricingPolicy.lineTotal(p.getSellingPrice(), item.getQuantity()),
                            BigDecimal::add);
                }
            } else {
                active++;
                activeValue = activeValue.add(live);
            }
        }
        long total = cartRepository.countCustomerCarts();
        long orders = orderRepository.countByStatusNotAndCreatedAtAfter(OrderStatus.PENDING_PAYMENT, now.minusDays(30));
        BigDecimal rate = abandoned + orders == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(abandoned * 100).divide(BigDecimal.valueOf(abandoned + orders), 1, RoundingMode.HALF_UP);
        BigDecimal average = abandoned == 0 ? BigDecimal.ZERO
                : abandonedValue.divide(BigDecimal.valueOf(abandoned), 2, RoundingMode.HALF_UP);

        List<AbandonedProduct> top = units.entrySet().stream()
                .map(e -> {
                    Product p = products.get(e.getKey());
                    return new AbandonedProduct(p.getProductId(), p.getProductName(), p.getImageUrl(), e.getValue()[0],
                            e.getValue()[1], values.get(e.getKey()).setScale(2, RoundingMode.HALF_UP));
                })
                .sorted(Comparator.comparingLong(AbandonedProduct::carts).reversed()
                        .thenComparing(AbandonedProduct::value, Comparator.reverseOrder()))
                .limit(5)
                .toList();
        return new CartSummaryResponse(hours, total, active, abandoned, total - active - abandoned, money(activeValue),
                money(abandonedValue), average, orders, rate, top);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminCartDetailResponse get(Long cartId, int abandonedAfterHours) {
        Cart cart = findCart(cartId);
        CartResponse response = CartServiceImpl.toResponse(cart, settingsService.get());
        return new AdminCartDetailResponse(summarise(cart, response, clampHours(abandonedAfterHours), LocalDateTime.now()),
                response);
    }

    @Override
    @Transactional
    public AdminCartResponse remind(Long cartId, int abandonedAfterHours) {
        int hours = clampHours(abandonedAfterHours);
        Cart cart = findCart(cartId);
        LocalDateTime now = LocalDateTime.now();
        if (!isAbandoned(cart, now.minusHours(hours))) {
            throw ApiException.badRequest("NOT_ABANDONED", "Reminders are only for abandoned carts (items left for "
                    + hours + "+ hours).");
        }
        if (!cart.getUser().isActive()) {
            throw ApiException.badRequest("CUSTOMER_INACTIVE", "This customer's account is inactive.");
        }
        if (cart.getReminderSentAt() != null && cart.getReminderSentAt().isAfter(cart.getUpdatedAt())) {
            throw ApiException.conflict("ALREADY_REMINDED",
                    "A reminder was already sent for this cart. Wait for the customer to come back first.");
        }
        CartResponse response = CartServiceImpl.toResponse(cart, settingsService.get());
        List<String> names = cart.getItems().stream().map(i -> i.getProduct().getProductName()).toList();
        String list = names.size() <= 2 ? String.join(" and ", names)
                : names.get(0) + ", " + names.get(1) + " and " + (names.size() - 2) + " more";
        notificationService.notify(cart.getUser(), "Your cart is waiting",
                "You left " + list + " in your cart" + (response.subtotal().signum() > 0
                        ? " (₹" + response.subtotal() + ")" : "") + ". Complete your order before it sells out.",
                "/cart");
        cartRepository.markReminderSent(cartId, now);
        Cart reloaded = findCart(cartId);
        return summarise(reloaded, CartServiceImpl.toResponse(reloaded, settingsService.get()), hours, now);
    }

    // --------------------------------------------------------------- helpers

    static boolean isAbandoned(Cart cart, LocalDateTime cutoff) {
        return !cart.getItems().isEmpty() && cart.getUpdatedAt().isBefore(cutoff);
    }

    static String stateOf(Cart cart, LocalDateTime cutoff) {
        if (cart.getItems().isEmpty()) {
            return EMPTY;
        }
        return isAbandoned(cart, cutoff) ? ABANDONED : ACTIVE;
    }

    private static AdminCartResponse summarise(Cart cart, CartResponse live, int hours, LocalDateTime now) {
        int unavailable = (int) live.items().stream().filter(i -> !i.available()).count();
        return new AdminCartResponse(cart.getCartId(), cart.getUser().getId(), cart.getUser().getFullName(),
                cart.getUser().getEmail(), cart.getUser().isActive(), stateOf(cart, now.minusHours(hours)),
                live.items().size(), live.itemCount(), live.subtotal(), unavailable,
                live.items().stream().map(CartItemResponse::productName).limit(3).toList(),
                Math.max(Duration.between(cart.getUpdatedAt(), now).toHours(), 0),
                cart.getCreatedAt(), cart.getUpdatedAt(), cart.getReminderSentAt());
    }

    private Cart findCart(Long cartId) {
        return cartRepository.findWithItemsByCartId(cartId)
                .orElseThrow(() -> ApiException.notFound("Cart " + cartId + " was not found."));
    }

    private static int clampHours(int hours) {
        return Math.min(Math.max(hours, 1), 24 * 30);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
