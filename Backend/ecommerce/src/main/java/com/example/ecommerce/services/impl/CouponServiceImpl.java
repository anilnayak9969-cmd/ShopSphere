package com.example.ecommerce.services.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dtos.cart.CartResponse;
import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.coupon.CouponQuote;
import com.example.ecommerce.dtos.coupon.CouponRequest;
import com.example.ecommerce.dtos.coupon.CouponResponse;
import com.example.ecommerce.entites.Coupon;
import com.example.ecommerce.entites.CouponRedemption;
import com.example.ecommerce.entites.CustomerOrder;
import com.example.ecommerce.enums.DiscountType;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.CouponRedemptionRepository;
import com.example.ecommerce.repositories.CouponRepository;
import com.example.ecommerce.services.CartService;
import com.example.ecommerce.services.CouponService;
import com.example.ecommerce.services.PricingPolicy;
import com.example.ecommerce.services.SettingsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;
    private final CartService cartService;
    private final SettingsService settingsService;

    // ----------------------------------------------------------------- admin

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CouponResponse> list(int page, int size) {
        return PageResponse.of(couponRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50))), CouponResponse::from);
    }

    @Override
    @Transactional
    public CouponResponse create(CouponRequest request) {
        String code = normalize(request.code());
        if (couponRepository.existsByCodeIgnoreCase(code)) {
            throw ApiException.conflict("DUPLICATE_COUPON", "A coupon with code " + code + " already exists.");
        }
        Coupon coupon = new Coupon();
        apply(coupon, request, code);
        return CouponResponse.from(couponRepository.saveAndFlush(coupon));
    }

    @Override
    @Transactional
    public CouponResponse update(Long couponId, CouponRequest request) {
        Coupon coupon = find(couponId);
        String code = normalize(request.code());
        if (couponRepository.existsByCodeIgnoreCaseAndCouponIdNot(code, couponId)) {
            throw ApiException.conflict("DUPLICATE_COUPON", "A coupon with code " + code + " already exists.");
        }
        apply(coupon, request, code);
        return CouponResponse.from(couponRepository.saveAndFlush(coupon));
    }

    @Override
    @Transactional
    public CouponResponse setActive(Long couponId, boolean active) {
        Coupon coupon = find(couponId);
        coupon.setActive(active);
        return CouponResponse.from(couponRepository.saveAndFlush(coupon));
    }

    // -------------------------------------------------------------- customer

    @Override
    @Transactional(readOnly = true)
    public CouponQuote quote(Long userId, String code) {
        CartResponse cart = cartService.getCart(userId);
        if (cart.items().isEmpty()) {
            throw ApiException.badRequest("CART_EMPTY", "Add items to your cart before applying a coupon.");
        }
        AppliedCoupon applied = resolve(userId, code, cart.subtotal());
        BigDecimal afterDiscount = cart.subtotal().subtract(applied.discount());
        BigDecimal shipping = PricingPolicy.shippingFor(afterDiscount, settingsService.get());
        return new CouponQuote(applied.coupon().getCode(), applied.coupon().getDescription(), cart.subtotal(),
                applied.discount(), shipping, afterDiscount.add(shipping));
    }

    @Override
    @Transactional(readOnly = true)
    public AppliedCoupon resolve(Long userId, String rawCode, BigDecimal subtotal) {
        String code = normalize(rawCode);
        Coupon coupon = couponRepository.findByCodeIgnoreCase(code).filter(Coupon::isActive)
                .orElseThrow(() -> ApiException.badRequest("COUPON_INVALID", "“" + code + "” is not a valid coupon code."));
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getValidFrom() != null && now.isBefore(coupon.getValidFrom())) {
            throw ApiException.badRequest("COUPON_NOT_STARTED", "This coupon is not active yet.");
        }
        if (coupon.getValidUntil() != null && now.isAfter(coupon.getValidUntil())) {
            throw ApiException.badRequest("COUPON_EXPIRED", "This coupon has expired.");
        }
        if (coupon.getUsageLimit() != null && coupon.getUsedCount() >= coupon.getUsageLimit()) {
            throw ApiException.badRequest("COUPON_EXHAUSTED", "This coupon has reached its usage limit.");
        }
        if (redemptionRepository.existsByCouponCouponIdAndUserId(coupon.getCouponId(), userId)) {
            throw ApiException.badRequest("COUPON_USED", "You have already used this coupon.");
        }
        if (subtotal.compareTo(coupon.getMinOrderAmount()) < 0) {
            throw ApiException.badRequest("COUPON_MIN_ORDER", "Add ₹"
                    + coupon.getMinOrderAmount().subtract(subtotal).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                    + " more to use this coupon (minimum order ₹"
                    + coupon.getMinOrderAmount().stripTrailingZeros().toPlainString() + ").");
        }
        return new AppliedCoupon(coupon, discountFor(coupon, subtotal));
    }

    @Override
    @Transactional
    public void recordRedemption(CustomerOrder order) {
        if (order.getCouponCode() == null) {
            return;
        }
        Coupon coupon = couponRepository.findByCodeIgnoreCase(order.getCouponCode()).orElse(null);
        if (coupon == null) {
            log.warn("Coupon {} on order {} no longer exists", order.getCouponCode(), order.getOrderNumber());
            return;
        }
        Long userId = order.getCustomer().getId();
        if (redemptionRepository.existsByCouponCouponIdAndUserId(coupon.getCouponId(), userId)) {
            // Two unpaid checkouts used the same coupon and both were paid — honour it, but log it.
            log.warn("Coupon {} already redeemed by user {}; order {} still honoured", coupon.getCode(), userId,
                    order.getOrderNumber());
            return;
        }
        if (couponRepository.incrementUsage(coupon.getCouponId()) == 0) {
            log.warn("Coupon {} exceeded its usage limit on paid order {}", coupon.getCode(), order.getOrderNumber());
        }
        CouponRedemption redemption = new CouponRedemption();
        redemption.setCoupon(coupon);
        redemption.setUser(order.getCustomer());
        redemption.setOrder(order);
        redemption.setDiscountAmount(order.getDiscountAmount());
        redemptionRepository.save(redemption);
    }

    // --------------------------------------------------------------- helpers

    /** PERCENT: % of subtotal, capped by maxDiscount. FLAT: fixed rupees. Never more than the subtotal. */
    static BigDecimal discountFor(Coupon coupon, BigDecimal subtotal) {
        BigDecimal discount;
        if (coupon.getDiscountType() == DiscountType.PERCENT) {
            discount = subtotal.multiply(coupon.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (coupon.getMaxDiscount() != null && discount.compareTo(coupon.getMaxDiscount()) > 0) {
                discount = coupon.getMaxDiscount();
            }
        } else {
            discount = coupon.getDiscountValue();
        }
        return discount.min(subtotal).setScale(2, RoundingMode.HALF_UP);
    }

    private void apply(Coupon coupon, CouponRequest r, String code) {
        if (r.discountType() == DiscountType.PERCENT && r.discountValue().compareTo(BigDecimal.valueOf(90)) > 0) {
            throw ApiException.badRequest("INVALID_DISCOUNT", "A percentage discount cannot exceed 90%.");
        }
        if (r.validFrom() != null && r.validUntil() != null && !r.validUntil().isAfter(r.validFrom())) {
            throw ApiException.badRequest("INVALID_DATES", "The end date must be after the start date.");
        }
        coupon.setCode(code);
        coupon.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        coupon.setDiscountType(r.discountType());
        coupon.setDiscountValue(r.discountValue());
        coupon.setMinOrderAmount(r.minOrderAmount() == null ? BigDecimal.ZERO : r.minOrderAmount());
        coupon.setMaxDiscount(r.discountType() == DiscountType.PERCENT ? r.maxDiscount() : null);
        coupon.setValidFrom(r.validFrom());
        coupon.setValidUntil(r.validUntil());
        coupon.setUsageLimit(r.usageLimit());
        coupon.setActive(r.isActive());
    }

    private Coupon find(Long couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> ApiException.notFound("Coupon " + couponId + " was not found."));
    }

    private static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
