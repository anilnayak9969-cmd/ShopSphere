package com.example.ecommerce.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.ecommerce.enums.OrderStatus;

/** Pure business rules: order status derivation, refund shares, commission and upload type detection. */
class OrderRulesTest {

    @Test
    void orderStatusFollowsTheLeastAdvancedActiveItem() {
        assertThat(OrderStatus.deriveOrderStatus(List.of(OrderStatus.SHIPPED, OrderStatus.CONFIRMED)))
                .isEqualTo(OrderStatus.CONFIRMED);
        assertThat(OrderStatus.deriveOrderStatus(List.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED)))
                .isEqualTo(OrderStatus.DELIVERED);
        assertThat(OrderStatus.deriveOrderStatus(List.of(OrderStatus.DELIVERED, OrderStatus.RETURN_REQUESTED)))
                .isEqualTo(OrderStatus.RETURN_REQUESTED);
        assertThat(OrderStatus.deriveOrderStatus(List.of(OrderStatus.CANCELLED, OrderStatus.CANCELLED)))
                .isEqualTo(OrderStatus.CANCELLED);
        assertThat(OrderStatus.deriveOrderStatus(List.of(OrderStatus.REFUNDED, OrderStatus.CANCELLED)))
                .isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void itemTransitionsFollowTheFulfilmentPath() {
        assertThat(OrderStatus.PLACED.canTransitionTo(OrderStatus.CONFIRMED)).isTrue();
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.PACKED)).isTrue();
        assertThat(OrderStatus.PACKED.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
        assertThat(OrderStatus.PLACED.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.RETURNED.canTransitionTo(OrderStatus.REFUNDED)).isTrue();
    }

    @Test
    void refundShareSubtractsTheItemsPartOfTheCoupon() {
        // Order: two lines of 1000 + 500 = 1500 with a 150 coupon. The 1000 line carries 100 of it.
        assertThat(PricingPolicy.refundShare(new BigDecimal("1000"), new BigDecimal("1500"), new BigDecimal("150")))
                .isEqualByComparingTo("900.00");
        assertThat(PricingPolicy.refundShare(new BigDecimal("500"), new BigDecimal("1500"), BigDecimal.ZERO))
                .isEqualByComparingTo("500.00");
    }

    @Test
    void commissionIsAPercentageOfGrossSales() {
        assertThat(PricingPolicy.commission(new BigDecimal("12345.67"), new BigDecimal("10")))
                .isEqualByComparingTo("1234.57");
    }

    @Test
    void uploadTypeIsDetectedFromFileBytesNotTheName() {
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] webp = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        byte[] html = "<script>alert(1)</script>".getBytes();
        assertThat(FileStorageService.detectImageType(jpg)).isEqualTo("jpg");
        assertThat(FileStorageService.detectImageType(png)).isEqualTo("png");
        assertThat(FileStorageService.detectImageType(webp)).isEqualTo("webp");
        assertThat(FileStorageService.detectImageType(html)).isNull();
    }
}
