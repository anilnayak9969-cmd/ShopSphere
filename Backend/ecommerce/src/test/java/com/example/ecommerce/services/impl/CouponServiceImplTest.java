package com.example.ecommerce.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.ecommerce.entites.Coupon;
import com.example.ecommerce.enums.DiscountType;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.CouponRedemptionRepository;
import com.example.ecommerce.repositories.CouponRepository;
import com.example.ecommerce.services.CartService;
import com.example.ecommerce.services.CouponService.AppliedCoupon;

@ExtendWith(MockitoExtension.class)
class CouponServiceImplTest {

    @Mock private CouponRepository couponRepository;
    @Mock private CouponRedemptionRepository redemptionRepository;
    @Mock private CartService cartService;

    @InjectMocks
    private CouponServiceImpl couponService;

    @Test
    void percentDiscountIsCappedByMaxDiscount() {
        Coupon c = coupon(DiscountType.PERCENT, "10", "0", "500");
        assertThat(CouponServiceImpl.discountFor(c, new BigDecimal("2000"))).isEqualByComparingTo("200.00");
        assertThat(CouponServiceImpl.discountFor(c, new BigDecimal("9000"))).isEqualByComparingTo("500.00");
    }

    @Test
    void flatDiscountNeverExceedsSubtotal() {
        Coupon c = coupon(DiscountType.FLAT, "300", "0", null);
        assertThat(CouponServiceImpl.discountFor(c, new BigDecimal("1000"))).isEqualByComparingTo("300.00");
        assertThat(CouponServiceImpl.discountFor(c, new BigDecimal("199"))).isEqualByComparingTo("199.00");
    }

    @Test
    void validCodeIsResolvedCaseInsensitively() {
        Coupon c = coupon(DiscountType.PERCENT, "10", "499", "500");
        when(couponRepository.findByCodeIgnoreCase("WELCOME10")).thenReturn(Optional.of(c));
        when(redemptionRepository.existsByCouponCouponIdAndUserId(1L, 7L)).thenReturn(false);

        AppliedCoupon applied = couponService.resolve(7L, " welcome10 ", new BigDecimal("1000"));

        assertThat(applied.discount()).isEqualByComparingTo("100.00");
    }

    @Test
    void minimumOrderIsEnforced() {
        Coupon c = coupon(DiscountType.PERCENT, "10", "499", "500");
        when(couponRepository.findByCodeIgnoreCase("WELCOME10")).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> couponService.resolve(7L, "WELCOME10", new BigDecimal("300")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("COUPON_MIN_ORDER"))
                .hasMessageContaining("Add ₹199");
    }

    @Test
    void expiredInactiveExhaustedAndReusedCouponsAreRejected() {
        Coupon expired = coupon(DiscountType.FLAT, "50", "0", null);
        expired.setValidUntil(LocalDateTime.now().minusDays(1));
        when(couponRepository.findByCodeIgnoreCase("OLD")).thenReturn(Optional.of(expired));
        assertCode("OLD", "COUPON_EXPIRED");

        Coupon inactive = coupon(DiscountType.FLAT, "50", "0", null);
        inactive.setActive(false);
        when(couponRepository.findByCodeIgnoreCase("OFF")).thenReturn(Optional.of(inactive));
        assertCode("OFF", "COUPON_INVALID");

        Coupon exhausted = coupon(DiscountType.FLAT, "50", "0", null);
        exhausted.setUsageLimit(5);
        exhausted.setUsedCount(5);
        when(couponRepository.findByCodeIgnoreCase("GONE")).thenReturn(Optional.of(exhausted));
        assertCode("GONE", "COUPON_EXHAUSTED");

        Coupon used = coupon(DiscountType.FLAT, "50", "0", null);
        when(couponRepository.findByCodeIgnoreCase("ONCE")).thenReturn(Optional.of(used));
        when(redemptionRepository.existsByCouponCouponIdAndUserId(1L, 7L)).thenReturn(true);
        assertCode("ONCE", "COUPON_USED");
    }

    private void assertCode(String code, String expectedError) {
        assertThatThrownBy(() -> couponService.resolve(7L, code, new BigDecimal("1000")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(expectedError));
    }

    private static Coupon coupon(DiscountType type, String value, String min, String max) {
        Coupon c = new Coupon();
        c.setCouponId(1L);
        c.setCode("WELCOME10");
        c.setDiscountType(type);
        c.setDiscountValue(new BigDecimal(value));
        c.setMinOrderAmount(new BigDecimal(min));
        c.setMaxDiscount(max == null ? null : new BigDecimal(max));
        c.setActive(true);
        return c;
    }
}
