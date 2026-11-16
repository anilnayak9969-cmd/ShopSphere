package com.example.ecommerce.services.impl;

import static com.example.ecommerce.services.impl.TestCatalog.customer;
import static com.example.ecommerce.services.impl.TestCatalog.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.ecommerce.dtos.cart.AddToCartRequest;
import com.example.ecommerce.dtos.cart.AdminCartResponse;
import com.example.ecommerce.dtos.cart.CartResponse;
import com.example.ecommerce.dtos.cart.CartSummaryResponse;
import com.example.ecommerce.entites.Cart;
import com.example.ecommerce.entites.CartItem;
import com.example.ecommerce.entites.PlatformSettings;
import com.example.ecommerce.entites.Product;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.CartRepository;
import com.example.ecommerce.repositories.OrderRepository;
import com.example.ecommerce.repositories.ProductRepository;
import com.example.ecommerce.repositories.UserRepository;
import com.example.ecommerce.services.NotificationService;
import com.example.ecommerce.services.SettingsService;
import com.example.ecommerce.services.WishlistService;

/** Cart Management module: add / update / remove with stock checks, stored totals, and abandonment analysis. */
@ExtendWith(MockitoExtension.class)
class CartManagementTest {

    @Mock private CartRepository cartRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private SettingsService settingsService;
    @Mock private WishlistService wishlistService;
    @Mock private OrderRepository orderRepository;
    @Mock private NotificationService notificationService;

    private CartServiceImpl carts;
    private CartAdminServiceImpl admin;
    private final User priya = customer(8L, "Priya Sharma");
    private final Product lamp = product(79L, "Nordic Dome Pendant Light", "2023.12", 3);
    private final Product book = product(63L, "Small Steps, Big Habits", "399.20", 75);

    @BeforeEach
    void setUp() {
        carts = new CartServiceImpl(cartRepository, productRepository, userRepository, settingsService, wishlistService);
        admin = new CartAdminServiceImpl(cartRepository, orderRepository, settingsService, notificationService);
        lenient().when(settingsService.get()).thenReturn(new PlatformSettings());
        lenient().when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // -------------------------------------------------------------- add to cart

    @Test
    void addToCartChecksStockAndStoresTheTotals() {
        Cart cart = cart(priya);
        when(productRepository.findWithDetailsByProductId(63L)).thenReturn(Optional.of(book));
        when(cartRepository.findByUserId(8L)).thenReturn(Optional.of(cart));

        CartResponse response = carts.addItem(8L, new AddToCartRequest(63L, 2));

        assertThat(response.itemCount()).isEqualTo(2);
        assertThat(response.subtotal()).isEqualByComparingTo("798.40");
        assertThat(cart.getTotalPrice()).isEqualByComparingTo("798.40");
        assertThat(cart.getTotalQuantity()).isEqualTo(2);
        assertThat(cart.getItems().get(0).getLineTotal()).isEqualByComparingTo("798.40");
    }

    @Test
    void addingMoreThanTheStockIsRefused() {
        when(productRepository.findWithDetailsByProductId(79L)).thenReturn(Optional.of(lamp));
        when(cartRepository.findByUserId(8L)).thenReturn(Optional.of(cart(priya)));

        assertThatThrownBy(() -> carts.addItem(8L, new AddToCartRequest(79L, 4)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Only 3 unit(s)");
        verify(cartRepository, never()).save(any());
    }

    @Test
    void addingAnOutOfStockProductIsRefused() {
        when(productRepository.findWithDetailsByProductId(70L)).thenReturn(Optional.of(product(70L, "Kettlebell", "1799.10", 0)));
        when(cartRepository.findByUserId(8L)).thenReturn(Optional.of(cart(priya)));

        assertThatThrownBy(() -> carts.addItem(8L, new AddToCartRequest(70L, 1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("out of stock");
    }

    @Test
    void addingTheSameProductAgainIncreasesItsQuantity() {
        Cart cart = cart(priya, line(book, 1));
        when(productRepository.findWithDetailsByProductId(63L)).thenReturn(Optional.of(book));
        when(cartRepository.findByUserId(8L)).thenReturn(Optional.of(cart));

        carts.addItem(8L, new AddToCartRequest(63L, 2));

        assertThat(cart.getItems()).singleElement().satisfies(i -> assertThat(i.getQuantity()).isEqualTo(3));
    }

    // ------------------------------------------------------------- update / remove

    @Test
    void updatingTheQuantityRecalculatesTheTotal() {
        Cart cart = cart(priya, line(book, 1), line(lamp, 1));
        when(cartRepository.findByUserId(8L)).thenReturn(Optional.of(cart));

        CartResponse response = carts.updateItem(8L, cart.getItems().get(0).getCartItemId(), 3);

        assertThat(response.subtotal()).isEqualByComparingTo("3220.72"); // 3 × 399.20 + 2023.12
        assertThat(cart.getTotalPrice()).isEqualByComparingTo("3220.72");
        assertThat(cart.getTotalQuantity()).isEqualTo(4);
    }

    @Test
    void updatingAboveTheStockIsRefused() {
        Cart cart = cart(priya, line(lamp, 1));
        when(cartRepository.findByUserId(8L)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> carts.updateItem(8L, cart.getItems().get(0).getCartItemId(), 4))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Only 3 unit(s)");
        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(1);
    }

    @Test
    void removingAnItemRecalculatesTheTotal() {
        Cart cart = cart(priya, line(book, 2), line(lamp, 1));
        when(cartRepository.findByUserId(8L)).thenReturn(Optional.of(cart));

        CartResponse response = carts.removeItem(8L, cart.getItems().get(0).getCartItemId());

        assertThat(response.items()).singleElement().satisfies(i -> assertThat(i.productId()).isEqualTo(79L));
        assertThat(cart.getTotalPrice()).isEqualByComparingTo("2023.12");
        assertThat(cart.getTotalQuantity()).isEqualTo(1);
    }

    @Test
    void anotherCustomersCartItemCannotBeChanged() {
        when(cartRepository.findByUserId(8L)).thenReturn(Optional.of(cart(priya, line(book, 1))));

        assertThatThrownBy(() -> carts.updateItem(8L, 9999L, 2))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not in your cart");
    }

    // ------------------------------------------------------- admin: abandonment

    @Test
    void cartsAreActiveAbandonedOrEmpty() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        Cart active = idle(cart(priya, line(book, 1)), 2);
        Cart abandoned = idle(cart(customer(16L, "Rahul Mehta"), line(lamp, 1)), 72);
        Cart empty = idle(cart(customer(12L, "Anil Nayak")), 72);

        assertThat(CartAdminServiceImpl.stateOf(active, cutoff)).isEqualTo("ACTIVE");
        assertThat(CartAdminServiceImpl.stateOf(abandoned, cutoff)).isEqualTo("ABANDONED");
        assertThat(CartAdminServiceImpl.stateOf(empty, cutoff)).isEqualTo("EMPTY");
    }

    @Test
    void summaryWorksOutTheAbandonmentRateAndTopProducts() {
        Cart active = idle(cart(priya, line(book, 1)), 2);
        Cart abandoned = idle(cart(customer(16L, "Rahul Mehta"), line(lamp, 1), line(book, 2)), 72);
        when(cartRepository.findNonEmptyCustomerCarts()).thenReturn(List.of(active, abandoned));
        when(cartRepository.countCustomerCarts()).thenReturn(3L);
        when(orderRepository.countByStatusNotAndCreatedAtAfter(eq(OrderStatus.PENDING_PAYMENT), any())).thenReturn(7L);

        CartSummaryResponse summary = admin.summary(24);

        assertThat(summary.activeCarts()).isEqualTo(1);
        assertThat(summary.abandonedCarts()).isEqualTo(1);
        assertThat(summary.emptyCarts()).isEqualTo(1);
        assertThat(summary.abandonedValue()).isEqualByComparingTo("2821.52"); // 2023.12 + 2 × 399.20
        assertThat(summary.abandonmentRate()).isEqualByComparingTo("12.5"); // 1 ÷ (1 + 7)
        assertThat(summary.topAbandonedProducts()).extracting(CartSummaryResponse.AbandonedProduct::productName)
                .containsExactly("Nordic Dome Pendant Light", "Small Steps, Big Habits");
    }

    @Test
    void reminderIsSentOnlyForAbandonedCarts() {
        Cart active = idle(cart(priya, line(book, 1)), 2);
        when(cartRepository.findWithItemsByCartId(active.getCartId())).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> admin.remind(active.getCartId(), 24))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("only for abandoned carts");
        verify(notificationService, never()).notify(any(), anyString(), anyString(), anyString());
    }

    @Test
    void reminderNotifiesTheCustomerOnce() {
        User rahul = customer(16L, "Rahul Mehta");
        Cart abandoned = idle(cart(rahul, line(lamp, 1)), 72);
        when(cartRepository.findWithItemsByCartId(abandoned.getCartId())).thenReturn(Optional.of(abandoned));

        AdminCartResponse response = admin.remind(abandoned.getCartId(), 24);

        verify(notificationService).notify(eq(rahul), eq("Your cart is waiting"), anyString(), eq("/cart"));
        verify(cartRepository).markReminderSent(eq(abandoned.getCartId()), any());
        assertThat(response.state()).isEqualTo("ABANDONED");

        abandoned.setReminderSentAt(LocalDateTime.now());
        assertThatThrownBy(() -> admin.remind(abandoned.getCartId(), 24))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already sent");
    }

    // ---------------------------------------------------------------- helpers

    private static long nextId = 100;

    private static Cart cart(User owner, CartItem... lines) {
        Cart cart = new Cart();
        cart.setCartId(nextId++);
        cart.setUser(owner);
        cart.setItems(new ArrayList<>());
        for (CartItem line : lines) {
            line.setCart(cart);
            cart.getItems().add(line);
        }
        cart.markActivity();
        return cart;
    }

    private static CartItem line(Product p, int quantity) {
        CartItem item = new CartItem();
        item.setCartItemId(nextId++);
        item.setProduct(p);
        item.setQuantity(quantity);
        item.setPriceAtAdd(p.getSellingPrice());
        return item;
    }

    /** Sets the cart's last activity some hours ago. */
    private static Cart idle(Cart cart, int hours) {
        ReflectionTestUtils.setField(cart, "updatedAt", LocalDateTime.now().minusHours(hours));
        return cart;
    }
}
