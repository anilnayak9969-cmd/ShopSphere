package com.example.ecommerce.entites;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Single-row table (id = 1) holding business rules an admin can change without redeploying. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "platform_settings")
public class PlatformSettings extends AuditableEntity {

    public static final long SINGLETON_ID = 1L;

    @Id
    @Column(name = "settings_id")
    private Long id = SINGLETON_ID;

    /** Percentage of each delivered item's price kept by ShopSphere. */
    @Column(name = "commission_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionPercent = new BigDecimal("10.00");

    @Column(name = "free_shipping_threshold", nullable = false, precision = 12, scale = 2)
    private BigDecimal freeShippingThreshold = new BigDecimal("499.00");

    @Column(name = "shipping_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingFee = new BigDecimal("49.00");

    @Column(name = "cod_enabled", nullable = false)
    private boolean codEnabled = true;

    /** Orders above this total must be paid online. */
    @Column(name = "cod_max_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal codMaxAmount = new BigDecimal("10000.00");

    /** Days after delivery during which a customer may request a return. */
    @Column(name = "return_window_days", nullable = false)
    private int returnWindowDays = 7;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold = 5;
}
