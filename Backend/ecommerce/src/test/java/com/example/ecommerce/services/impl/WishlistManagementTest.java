package com.example.ecommerce.services.impl;

import static com.example.ecommerce.services.impl.TestCatalog.customer;
import static com.example.ecommerce.services.impl.TestCatalog.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.example.ecommerce.dtos.cart.CartResponse;
import com.example.ecommerce.dtos.wishlist.WishlistItemResponse;
import com.example.ecommerce.entites.Cart;
import com.example.ecommerce.entites.PlatformSettings;
import com.example.ecommerce.entites.Product;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.entites.WishlistItem;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.CartRepository;
import com.example.ecommerce.repositories.ProductRepository;
import com.example.ecommerce.repositories.UserRepository;
import com.example.ecommerce.repositories.WishlistItemRepository;
import com.example.ecommerce.services.SettingsService;
import com.example.ecommerce.services.WishlistService;

/** Wishlist Management module: add, view (availability), remove and move to cart. */
@ExtendWith(MockitoExtension.class)
class WishlistManagementTest {

    @Mock private WishlistItemRepository wishlistRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private CartRepository cartRepository;
    @Mock private SettingsService settingsService;
    @Mock private WishlistService wishlistServiceMock;

    private WishlistServiceImpl wishlist;
    private final User priya = customer(8L, "Priya Sharma");

    @BeforeEach
    void setUp() {
        wishlist = new WishlistServiceImpl(wishlistRepository, productRepository, userRepository);
        lenient().when(userRepository.getReferenceById(8L)).thenReturn(priya);
        lenient().when(settingsService.get()).thenReturn(new PlatformSettings());
    }

    // ------------------------------------------------------------- add to wishlist

    @Test
    void addSavesAProductThatIsOnSale() {
        Product lamp = product(43L, "Lumina Desk Lamp", "1519.20", 30);
        when(wishlistRepository.existsByUserIdAndProductProductId(8L, 43L)).thenReturn(false);
        when(productRepository.findWithDetailsByProductId(43L)).thenReturn(Optional.of(lamp));
        when(wishlistRepository.countByUserId(8L)).thenReturn(3L);

        wishlist.add(8L, 43L);

        verify(wishlistRepository).save(any(WishlistItem.class));
    }

    @Test
    void addingTheSameProductTwiceKeepsOneEntry() {
        when(wishlistRepository.existsByUserIdAndProductProductId(8L, 43L)).thenReturn(true);

        wishlist.add(8L, 43L);

        verify(wishlistRepository, never()).save(any());
        verify(productRepository, never()).findWithDetailsByProductId(anyLong());
    }

    @Test
    void aProductThatIsNotOnSaleCannotBeSaved() {
        Product hidden = product(50L, "Withdrawn Lamp", "999.00", 10);
        hidden.setActive(false);
        when(wishlistRepository.existsByUserIdAndProductProductId(8L, 50L)).thenReturn(false);
        when(productRepository.findWithDetailsByProductId(50L)).thenReturn(Optional.of(hidden));

        assertThatThrownBy(() -> wishlist.add(8L, 50L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not available");
        verify(wishlistRepository, never()).save(any());
    }

    @Test
    void theWishlistHoldsAtMostOneHundredProducts() {
        when(wishlistRepository.existsByUserIdAndProductProductId(8L, 43L)).thenReturn(false);
        when(productRepository.findWithDetailsByProductId(43L)).thenReturn(Optional.of(product(43L, "Lamp", "100", 5)));
        when(wishlistRepository.countByUserId(8L)).thenReturn(100L);

        assertThatThrownBy(() -> wishlist.add(8L, 43L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("up to 100");
    }

    // ----------------------------------------------------------- view wishlist

    @Test
    void viewShowsNamePriceAndAvailabilityStatus() {
        Product plenty = product(1L, "Desk Lamp", "1519.20", 30);
        Product few = product(2L, "Pendant Light", "2023.12", 3);
        Product none = product(3L, "Kettlebell", "1799.10", 0);
        Product withdrawn = product(4L, "Old Book", "314.10", 10);
        withdrawn.setActive(false);
        when(wishlistRepository.findByUserIdOrderByCreatedAtDesc(8L))
                .thenReturn(List.of(item(plenty), item(few), item(none), item(withdrawn)));

        List<WishlistItemResponse> items = wishlist.getWishlist(8L);

        assertThat(items).extracting(WishlistItemResponse::availability)
                .containsExactly("IN_STOCK", "LOW_STOCK", "OUT_OF_STOCK", "UNAVAILABLE");
        assertThat(items).extracting(WishlistItemResponse::availabilityLabel)
                .containsExactly("In stock", "Only 3 left", "Out of stock", "No longer available");
        assertThat(items.get(0).product().productName()).isEqualTo("Desk Lamp");
        assertThat(items.get(0).product().sellingPrice()).isEqualByComparingTo("1519.20");
        assertThat(items.get(3).available()).isFalse();
    }

    // ---------------------------------------------------------------- remove

    @Test
    void removeDeletesTheSavedProduct() {
        WishlistItem saved = item(product(43L, "Lamp", "100", 5));
        when(wishlistRepository.findByUserIdAndProductProductId(8L, 43L)).thenReturn(Optional.of(saved));

        wishlist.remove(8L, 43L);

        verify(wishlistRepository).delete(saved);
    }

    @Test
    void removingSomethingNotSavedIsHarmless() {
        when(wishlistRepository.findByUserIdAndProductProductId(8L, 43L)).thenReturn(Optional.empty());

        wishlist.remove(8L, 43L);

        verify(wishlistRepository, never()).delete(any());
    }

    // ---------------------------------------------------------- move to cart

    @Test
    void moveToCartAddsOneUnitAndRemovesItFromTheWishlist() {
        CartServiceImpl cartService = cartService();
        Product book = product(62L, "Tales from the Western Ghats", "314.10", 40);
        when(wishlistServiceMock.contains(8L, 62L)).thenReturn(true);
        when(productRepository.findWithDetailsByProductId(62L)).thenReturn(Optional.of(book));
        Cart cart = emptyCart();
        when(cartRepository.findByUserId(8L)).thenReturn(Optional.of(cart));
        when(cartRepository.save(cart)).thenReturn(cart);

        CartResponse response = cartService.moveFromWishlist(8L, 62L);

        assertThat(response.items()).singleElement().satisfies(i -> {
            assertThat(i.productId()).isEqualTo(62L);
            assertThat(i.quantity()).isEqualTo(1);
        });
        assertThat(response.subtotal()).isEqualByComparingTo("314.10");
        verify(wishlistServiceMock).remove(8L, 62L);
    }

    @Test
    void moveToCartOfAnOutOfStockProductLeavesTheWishlistUnchanged() {
        CartServiceImpl cartService = cartService();
        when(wishlistServiceMock.contains(8L, 70L)).thenReturn(true);
        when(productRepository.findWithDetailsByProductId(70L)).thenReturn(Optional.of(product(70L, "Kettlebell", "1799.10", 0)));
        when(cartRepository.findByUserId(8L)).thenReturn(Optional.of(emptyCart()));

        assertThatThrownBy(() -> cartService.moveFromWishlist(8L, 70L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("out of stock");
        verify(wishlistServiceMock, never()).remove(anyLong(), anyLong());
        verify(cartRepository, never()).save(any());
    }

    @Test
    void moveToCartOfAProductThatIsNotSavedIsRefused() {
        CartServiceImpl cartService = cartService();
        when(wishlistServiceMock.contains(8L, 62L)).thenReturn(false);

        assertThatThrownBy(() -> cartService.moveFromWishlist(8L, 62L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not in your wishlist");
        verify(productRepository, never()).findWithDetailsByProductId(anyLong());
    }

    // ---------------------------------------------------------------- helpers

    private CartServiceImpl cartService() {
        return new CartServiceImpl(cartRepository, productRepository, userRepository, settingsService, wishlistServiceMock);
    }

    private Cart emptyCart() {
        Cart cart = new Cart();
        cart.setCartId(4L);
        cart.setUser(priya);
        cart.setItems(new ArrayList<>());
        return cart;
    }

    private WishlistItem item(Product p) {
        WishlistItem w = new WishlistItem();
        w.setWishlistItemId(p.getProductId() * 10);
        w.setUser(priya);
        w.setProduct(p);
        return w;
    }
}
