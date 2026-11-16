package com.example.ecommerce.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import com.example.ecommerce.dtos.coupon.CouponRequest;
import com.example.ecommerce.dtos.coupon.CouponResponse;
import com.example.ecommerce.entites.Category;
import com.example.ecommerce.entites.Coupon;
import com.example.ecommerce.entites.CustomerOrder;
import com.example.ecommerce.entites.OrderItem;
import com.example.ecommerce.entites.Product;
import com.example.ecommerce.enums.CouponStatus;
import com.example.ecommerce.enums.DiscountType;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.CategoryRepository;
import com.example.ecommerce.repositories.CouponRedemptionRepository;
import com.example.ecommerce.repositories.CouponRepository;
import com.example.ecommerce.repositories.ProductRepository;
import com.example.ecommerce.services.CartService;
import com.example.ecommerce.services.CouponService.AppliedCoupon;
import com.example.ecommerce.services.CouponService.CouponLine;
import com.example.ecommerce.services.CouponService.CouponSummary;
import com.example.ecommerce.services.OrderSupport;
import com.example.ecommerce.services.SettingsService;

/** Coupon and Discount Management module: create, dashboard, apply at checkout, deactivate / soft delete. */
@ExtendWith(MockitoExtension.class)
class CouponManagementTest {

    @Mock private CouponRepository couponRepository;
    @Mock private CouponRedemptionRepository redemptionRepository;
    @Mock private CartService cartService;
    @Mock private SettingsService settingsService;
    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;

    @InjectMocks
    private CouponServiceImpl coupons;

    private Category electronics;
    private Category audio;
    private Category books;
    private Product headphones;
    private Product novel;

    @BeforeEach
    void setUp() {
        electronics = category(1, "Electronics", null);
        audio = category(2, "Audio", electronics);
        books = category(3, "Books", null);
        headphones = product(10L, "Aura Headphones", audio);
        novel = product(62L, "Tales from the Western Ghats", books);
        lenient().when(couponRepository.saveAndFlush(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------------------------------------------------- create a coupon

    @Test
    void adminCreatesAPercentageCouponLimitedToACategoryAndAProduct() {
        when(categoryRepository.findAllById(anyCollection())).thenReturn(List.of(electronics));
        when(productRepository.findAllById(anyCollection())).thenReturn(List.of(novel));

        CouponResponse c = coupons.create(request("  audio20 ", DiscountType.PERCENT, "20", "999", "300",
                List.of(62L), List.of(1)));

        assertThat(c.code()).isEqualTo("AUDIO20");
        assertThat(c.status()).isEqualTo(CouponStatus.ACTIVE);
        assertThat(c.appliesTo()).isEqualTo("Electronics · Tales from the Western Ghats");
        assertThat(c.categories()).extracting(CouponResponse.CategoryRef::categoryName).containsExactly("Electronics");
    }

    @Test
    void adminCreatesAFixedAmountCouponForTheWholeOrder() {
        CouponResponse c = coupons.create(request("FLAT150", DiscountType.FLAT, "150", "999", null, null, null));

        assertThat(c.discountType()).isEqualTo(DiscountType.FLAT);
        assertThat(c.maxDiscount()).isNull();
        assertThat(c.appliesTo()).isEqualTo("All products");
        verify(productRepository, never()).findAllById(anyCollection());
    }

    @Test
    void invalidCouponsAreRefused() {
        when(couponRepository.existsByCodeIgnoreCase("TAKEN")).thenReturn(true);
        assertThatThrownBy(() -> coupons.create(request("TAKEN", DiscountType.FLAT, "50", "0", null, null, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertError(request("BIG95", DiscountType.PERCENT, "95", "0", null, null, null), "INVALID_DISCOUNT");
        assertError(request("FLAT900", DiscountType.FLAT, "900", "500", null, null, null), "INVALID_DISCOUNT");
        when(productRepository.findAllById(anyCollection())).thenReturn(List.of());
        assertError(request("GHOST", DiscountType.FLAT, "50", "0", null, List.of(999L), null), "INVALID_PRODUCTS");
        CouponRequest badDates = new CouponRequest("DATES1", null, DiscountType.FLAT, new BigDecimal("50"), BigDecimal.ZERO,
                null, LocalDateTime.now().plusDays(5), LocalDateTime.now().plusDays(1), null, true, null, null);
        assertError(badDates, "INVALID_DATES");
    }

    // ---------------------------------------------------- coupon dashboard

    @Test
    void statusShowsActiveScheduledExpiredInactiveUsedUpAndDeleted() {
        LocalDateTime now = LocalDateTime.now();
        Coupon c = coupon(DiscountType.FLAT, "50");
        assertThat(c.statusAt(now)).isEqualTo(CouponStatus.ACTIVE);
        c.setValidFrom(now.plusDays(1));
        assertThat(c.statusAt(now)).isEqualTo(CouponStatus.SCHEDULED);
        c.setValidFrom(null);
        c.setUsageLimit(2);
        c.setUsedCount(2);
        assertThat(c.statusAt(now)).isEqualTo(CouponStatus.USED_UP);
        c.setActive(false);
        assertThat(c.statusAt(now)).isEqualTo(CouponStatus.INACTIVE);
        c.setValidUntil(now.minusMinutes(1));
        assertThat(c.statusAt(now)).isEqualTo(CouponStatus.EXPIRED); // expiry is shown even if it was paused
        c.setDeletedAt(now);
        assertThat(c.statusAt(now)).isEqualTo(CouponStatus.DELETED);
    }

    @Test
    void dashboardFiltersByStatusAndShowsTheDiscountGiven() {
        Coupon c = coupon(DiscountType.PERCENT, "10");
        when(couponRepository.adminSearch(eq("EXPIRED"), eq("welcome"), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(c)));
        when(redemptionRepository.discountGiven(anyCollection())).thenReturn(List.<Object[]>of(new Object[]{1L, new BigDecimal("420.50")}));

        var page = coupons.list(CouponStatus.EXPIRED, " welcome ", 0, 10);

        assertThat(page.content()).singleElement().satisfies(r -> assertThat(r.discountGiven()).isEqualByComparingTo("420.50"));
        // No status = every coupon that is not deleted.
        when(couponRepository.adminSearch(eq("ALL"), isNull(), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        assertThat(coupons.list(null, "", 0, 10).content()).isEmpty();
    }

    @Test
    void summaryCountsEachStatus() {
        Coupon active = coupon(DiscountType.FLAT, "50");
        Coupon expired = coupon(DiscountType.FLAT, "50");
        expired.setValidUntil(LocalDateTime.now().minusDays(1));
        Coupon deleted = coupon(DiscountType.FLAT, "50");
        deleted.setDeletedAt(LocalDateTime.now());
        when(couponRepository.findAll()).thenReturn(List.of(active, expired, deleted));
        when(redemptionRepository.totals()).thenReturn(List.<Object[]>of(new Object[]{3L, new BigDecimal("600")}));

        CouponSummary s = coupons.summary();

        assertThat(s.active()).isEqualTo(1);
        assertThat(s.expired()).isEqualTo(1);
        assertThat(s.deleted()).isEqualTo(1);
        assertThat(s.redemptions()).isEqualTo(3);
        assertThat(s.discountGiven()).isEqualByComparingTo("600.00");
    }

    // ---------------------------------------------------- apply at checkout

    @Test
    void aCategoryCouponDiscountsOnlyItemsInThatCategoryAndItsSubcategories() {
        Coupon c = coupon(DiscountType.PERCENT, "20");
        c.getCategories().add(electronics);
        stub(c);
        when(productRepository.findAllById(anyCollection())).thenReturn(List.of(headphones, novel));

        AppliedCoupon applied = coupons.resolve(7L, "audio20", List.of(new CouponLine(10L, new BigDecimal("2000")),
                new CouponLine(62L, new BigDecimal("500"))));

        // 20% of the headphones (Audio is a subcategory of Electronics) only, not of the book.
        assertThat(applied.discount()).isEqualByComparingTo("400.00");
        assertThat(applied.eligibleSubtotal()).isEqualByComparingTo("2000");
        assertThat(applied.eligibleProductIds()).containsExactly(10L);
    }

    @Test
    void aProductCouponWithNoEligibleItemInTheCartIsRefused() {
        Coupon c = coupon(DiscountType.FLAT, "100");
        c.getProducts().add(headphones);
        stub(c);
        when(productRepository.findAllById(anyCollection())).thenReturn(List.of(novel));

        assertThatThrownBy(() -> coupons.resolve(7L, "AUDIO20", List.of(new CouponLine(62L, new BigDecimal("900")))))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("COUPON_NOT_APPLICABLE"))
                .hasMessageContaining("Aura Headphones");
    }

    @Test
    void theMinimumOrderIsCheckedOnTheWholeOrderTotal() {
        Coupon c = coupon(DiscountType.PERCENT, "20");
        c.setMinOrderAmount(new BigDecimal("1000"));
        c.getProducts().add(headphones);
        stub(c);
        when(productRepository.findAllById(anyCollection())).thenReturn(List.of(headphones, novel));

        // Headphones ₹600 + book ₹500 = ₹1,100 order total, so the coupon applies (to the headphones only).
        AppliedCoupon applied = coupons.resolve(7L, "AUDIO20", List.of(new CouponLine(10L, new BigDecimal("600")),
                new CouponLine(62L, new BigDecimal("500"))));
        assertThat(applied.discount()).isEqualByComparingTo("120.00");
    }

    @Test
    void checkoutSharesTheDiscountOnlyAmongEligibleLinesAndRefundsUseIt() {
        Coupon c = coupon(DiscountType.FLAT, "100");
        c.getProducts().add(headphones);
        CustomerOrder order = new CustomerOrder();
        OrderItem a = line(headphones, "600");
        OrderItem b = line(novel, "500");
        order.addItem(a);
        order.addItem(b);
        order.setSubtotal(new BigDecimal("1100"));
        order.setDiscountAmount(new BigDecimal("100"));

        CheckoutServiceImpl.shareDiscount(order, new AppliedCoupon(c, new BigDecimal("100.00"), new BigDecimal("600"),
                java.util.Set.of(10L)));

        assertThat(a.getDiscountShare()).isEqualByComparingTo("100.00");
        assertThat(b.getDiscountShare()).isEqualByComparingTo("0.00");
        OrderSupport support = new OrderSupport(null, null);
        assertThat(support.refundShare(order, a)).isEqualByComparingTo("500.00");
        assertThat(support.refundShare(order, b)).isEqualByComparingTo("500.00");
    }

    @Test
    void aWholeOrderDiscountIsSplitInProportionWithTheRemainderOnTheLastLine() {
        Coupon c = coupon(DiscountType.FLAT, "100");
        CustomerOrder order = new CustomerOrder();
        OrderItem a = line(headphones, "100");
        OrderItem b = line(novel, "200");
        order.addItem(a);
        order.addItem(b);

        CheckoutServiceImpl.shareDiscount(order, new AppliedCoupon(c, new BigDecimal("100.00"), new BigDecimal("300"),
                java.util.Set.of()));

        assertThat(a.getDiscountShare()).isEqualByComparingTo("33.33");
        assertThat(b.getDiscountShare()).isEqualByComparingTo("66.67");
    }

    // ------------------------------------------------ deactivate / delete coupon

    @Test
    void deactivatedCouponsCannotBeUsedAndCanBeReactivated() {
        Coupon c = coupon(DiscountType.FLAT, "50");
        when(couponRepository.findById(1L)).thenReturn(Optional.of(c));
        stub(c);

        assertThat(coupons.setActive(1L, false).status()).isEqualTo(CouponStatus.INACTIVE);
        assertThatThrownBy(() -> coupons.resolve(7L, "AUDIO20", new BigDecimal("1000")))
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("COUPON_INVALID"));
        assertThat(coupons.setActive(1L, true).status()).isEqualTo(CouponStatus.ACTIVE);
    }

    @Test
    void deleteIsASoftDeleteAndRestoreBringsTheCouponBackInactive() {
        Coupon c = coupon(DiscountType.FLAT, "50");
        when(couponRepository.findById(1L)).thenReturn(Optional.of(c));
        stub(c);

        CouponResponse deleted = coupons.delete(1L);

        assertThat(deleted.status()).isEqualTo(CouponStatus.DELETED);
        assertThat(c.getDeletedAt()).isNotNull();
        assertThat(c.isActive()).isFalse(); // status column
        verify(couponRepository, never()).delete(any(Coupon.class));
        assertThatThrownBy(() -> coupons.resolve(7L, "AUDIO20", new BigDecimal("1000")))
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("COUPON_INVALID"));
        assertThatThrownBy(() -> coupons.setActive(1L, true))
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("COUPON_DELETED"));

        CouponResponse restored = coupons.restore(1L);
        assertThat(restored.status()).isEqualTo(CouponStatus.INACTIVE);
        assertThat(c.getDeletedAt()).isNull();
    }

    @Test
    void theCodeOfAUsedCouponCannotChange() {
        Coupon c = coupon(DiscountType.FLAT, "50");
        c.setUsedCount(3);
        when(couponRepository.findById(1L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> coupons.update(1L, request("NEWCODE", DiscountType.FLAT, "50", "0", null, null, null)))
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("COUPON_IN_USE"));
    }

    // ------------------------------------------------------------------ helpers

    private void stub(Coupon c) {
        lenient().when(couponRepository.findByCodeIgnoreCase("AUDIO20")).thenReturn(Optional.of(c));
    }

    private void assertError(CouponRequest request, String code) {
        assertThatThrownBy(() -> coupons.create(request)).isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(code));
    }

    private static CouponRequest request(String code, DiscountType type, String value, String min, String max,
                                         List<Long> productIds, List<Integer> categoryIds) {
        return new CouponRequest(code, "Test coupon", type, new BigDecimal(value), new BigDecimal(min),
                max == null ? null : new BigDecimal(max), null, LocalDateTime.now().plusDays(30), null, true,
                productIds, categoryIds);
    }

    private static Coupon coupon(DiscountType type, String value) {
        Coupon c = new Coupon();
        c.setCouponId(1L);
        c.setCode("AUDIO20");
        c.setDiscountType(type);
        c.setDiscountValue(new BigDecimal(value));
        c.setMinOrderAmount(BigDecimal.ZERO);
        c.setActive(true);
        return c;
    }

    private static Category category(int id, String name, Category parent) {
        Category c = new Category();
        c.setCategoryId(id);
        c.setCategoryName(name);
        c.setParent(parent);
        return c;
    }

    private static Product product(Long id, String name, Category category) {
        Product p = new Product();
        p.setProductId(id);
        p.setProductName(name);
        p.setCategory(category);
        return p;
    }

    private static OrderItem line(Product product, String total) {
        OrderItem i = new OrderItem();
        i.setProduct(product);
        i.setLineTotal(new BigDecimal(total));
        return i;
    }
}
