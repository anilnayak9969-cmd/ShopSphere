package com.example.ecommerce.services.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dtos.cart.AddToCartRequest;
import com.example.ecommerce.dtos.cart.CartItemResponse;
import com.example.ecommerce.dtos.cart.CartResponse;
import com.example.ecommerce.entites.Cart;
import com.example.ecommerce.entites.CartItem;
import com.example.ecommerce.entites.PlatformSettings;
import com.example.ecommerce.entites.Product;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.CartRepository;
import com.example.ecommerce.repositories.ProductRepository;
import com.example.ecommerce.repositories.UserRepository;
import com.example.ecommerce.services.CartService;
import com.example.ecommerce.services.PricingPolicy;
import com.example.ecommerce.services.SettingsService;
import com.example.ecommerce.services.WishlistService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    static final int MAX_QUANTITY_PER_ITEM = 10;

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final SettingsService settingsService;
    private final WishlistService wishlistService;

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        PlatformSettings settings = settingsService.get();
        return cartRepository.findByUserId(userId).map(c -> toResponse(c, settings)).orElseGet(() -> empty(settings));
    }

    @Override
    @Transactional
    public CartResponse addItem(Long userId, AddToCartRequest request) {
        Product product = productRepository.findWithDetailsByProductId(request.productId())
                .filter(ProductServiceImpl::isPubliclyVisible)
                .orElseThrow(() -> ApiException.notFound("This product is not available."));
        Cart cart = getOrCreateCart(userId);

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getProduct().getProductId().equals(product.getProductId()))
                .findFirst()
                .orElse(null);
        int newQuantity = (item == null ? 0 : item.getQuantity()) + request.quantity();
        checkQuantity(product, newQuantity);

        if (item == null) {
            item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
            cart.getItems().add(item);
        }
        item.setQuantity(newQuantity);
        item.setPriceAtAdd(product.getSellingPrice());
        return toResponse(cartRepository.save(cart), settingsService.get());
    }

    @Override
    @Transactional
    public CartResponse updateItem(Long userId, Long cartItemId, int quantity) {
        Cart cart = findCart(userId);
        CartItem item = findItem(cart, cartItemId);
        checkQuantity(item.getProduct(), quantity);
        item.setQuantity(quantity);
        return toResponse(cartRepository.save(cart), settingsService.get());
    }

    @Override
    @Transactional
    public CartResponse removeItem(Long userId, Long cartItemId) {
        Cart cart = findCart(userId);
        cart.getItems().remove(findItem(cart, cartItemId)); // orphanRemoval deletes the row
        return toResponse(cartRepository.save(cart), settingsService.get());
    }

    @Override
    @Transactional
    public CartResponse saveForLater(Long userId, Long cartItemId) {
        Cart cart = findCart(userId);
        CartItem item = findItem(cart, cartItemId);
        wishlistService.add(userId, item.getProduct().getProductId());
        cart.getItems().remove(item);
        return toResponse(cartRepository.save(cart), settingsService.get());
    }

    @Override
    @Transactional
    public CartResponse acknowledgePrices(Long userId) {
        Cart cart = findCart(userId);
        cart.getItems().forEach(i -> i.setPriceAtAdd(i.getProduct().getSellingPrice()));
        return toResponse(cartRepository.save(cart), settingsService.get());
    }

    // --------------------------------------------------------------- helpers

    private Cart findCart(Long userId) {
        return cartRepository.findByUserId(userId).orElseThrow(() -> ApiException.notFound("Your cart is empty."));
    }

    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> {
            Cart cart = new Cart();
            cart.setUser(userRepository.getReferenceById(userId));
            return cartRepository.save(cart);
        });
    }

    /** Searching inside the user's own cart means nobody can edit another customer's cart item. */
    private static CartItem findItem(Cart cart, Long cartItemId) {
        return cart.getItems().stream().filter(i -> i.getCartItemId().equals(cartItemId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("That item is not in your cart."));
    }

    private static void checkQuantity(Product product, int quantity) {
        if (quantity > MAX_QUANTITY_PER_ITEM) {
            throw ApiException.badRequest("QUANTITY_LIMIT", "You can buy at most " + MAX_QUANTITY_PER_ITEM
                    + " units of a product per order.");
        }
        if (quantity > product.getStockQuantity()) {
            throw ApiException.conflict("INSUFFICIENT_STOCK", product.getStockQuantity() == 0
                    ? product.getProductName() + " is out of stock."
                    : "Only " + product.getStockQuantity() + " unit(s) of " + product.getProductName() + " are left.");
        }
    }

    /** Prices are always read from the product table right now — the cart only remembers the old price to flag changes. */
    static CartResponse toResponse(Cart cart, PlatformSettings settings) {
        List<CartItemResponse> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int count = 0;
        boolean anyAvailable = false;
        boolean anyPriceChanged = false;

        for (CartItem item : cart.getItems()) {
            Product p = item.getProduct();
            String message = null;
            boolean available = true;
            if (!ProductServiceImpl.isPubliclyVisible(p)) {
                available = false;
                message = "No longer available";
            } else if (p.getStockQuantity() == 0) {
                available = false;
                message = "Out of stock";
            } else if (p.getStockQuantity() < item.getQuantity()) {
                available = false;
                message = "Only " + p.getStockQuantity() + " left — reduce the quantity";
            }
            boolean priceChanged = item.getPriceAtAdd() != null && item.getPriceAtAdd().compareTo(p.getSellingPrice()) != 0;
            BigDecimal lineTotal = PricingPolicy.lineTotal(p.getSellingPrice(), item.getQuantity());
            if (available) {
                subtotal = subtotal.add(lineTotal);
                count += item.getQuantity();
                anyAvailable = true;
                anyPriceChanged |= priceChanged;
            }
            items.add(new CartItemResponse(item.getCartItemId(), p.getProductId(), p.getProductName(), p.getImageUrl(),
                    p.getSeller().getStoreName(), p.getPrice(), p.getSellingPrice(), p.getDiscountPercent(),
                    item.getQuantity(), lineTotal, p.getStockQuantity(), available, message, priceChanged,
                    priceChanged ? item.getPriceAtAdd() : null));
        }
        boolean allAvailable = items.stream().allMatch(CartItemResponse::available);
        BigDecimal shipping = PricingPolicy.shippingFor(subtotal, settings);
        return new CartResponse(items, count, subtotal.setScale(2), shipping, subtotal.add(shipping).setScale(2),
                settings.getFreeShippingThreshold(), anyAvailable && allAvailable, anyPriceChanged);
    }

    private static CartResponse empty(PlatformSettings settings) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        return new CartResponse(List.of(), 0, zero, zero, zero, settings.getFreeShippingThreshold(), false, false);
    }
}
