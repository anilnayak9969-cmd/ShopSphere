package com.example.ecommerce.entites;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The unique constraint stops the same product appearing twice in one cart. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cart_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_cart_items_cart_product", columnNames = {"cart_id", "product_id"}))
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_item_id")
    private Long cartItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    /** Selling price when the item was added or last reviewed — lets the cart flag price changes. */
    @Column(name = "price_at_add", precision = 12, scale = 2)
    private BigDecimal priceAtAdd;

    /** Cart Management: unit price × quantity, as of the last change. */
    @Column(name = "line_total", nullable = false, precision = 12, scale = 2,
            columnDefinition = "decimal(12,2) not null default 0.00")
    private BigDecimal lineTotal = BigDecimal.ZERO;

    @Column(name = "added_at", nullable = false, updatable = false,
            columnDefinition = "datetime(6) not null default current_timestamp(6)")
    private LocalDateTime addedAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false,
            columnDefinition = "datetime(6) not null default current_timestamp(6)")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
