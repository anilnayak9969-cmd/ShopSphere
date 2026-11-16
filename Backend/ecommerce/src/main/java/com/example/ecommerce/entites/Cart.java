package com.example.ecommerce.entites;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One persistent cart per customer. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "carts")
public class Cart extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_id")
    private Long cartId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** orphanRemoval: removing an item from this list deletes its row. */
    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("cartItemId ASC")
    private List<CartItem> items = new ArrayList<>();

    /**
     * Cart Management: total price of the items that can be bought, as of the last change.
     * (The cart page and checkout always recalculate from current product prices.)
     */
    @Column(name = "total_price", nullable = false, precision = 12, scale = 2,
            columnDefinition = "decimal(12,2) not null default 0.00")
    private BigDecimal totalPrice = BigDecimal.ZERO;

    /** Number of units in the cart, as of the last change. */
    @Column(name = "total_quantity", nullable = false, columnDefinition = "int not null default 0")
    private int totalQuantity;

    /** When an admin last sent an "items left in your cart" reminder (cart abandonment follow-up). */
    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    /** Records customer activity: updated_at is what cart-abandonment analysis looks at. */
    public void markActivity() {
        touch();
    }
}
