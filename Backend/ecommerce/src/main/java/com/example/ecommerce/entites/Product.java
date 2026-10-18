package com.example.ecommerce.entites;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.example.ecommerce.enums.ApprovalStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "products",
        uniqueConstraints = @UniqueConstraint(name = "uk_products_sku", columnNames = "sku"),
        indexes = {
                @Index(name = "idx_products_category", columnList = "category_id"),
                @Index(name = "idx_products_seller", columnList = "seller_id"),
                @Index(name = "idx_products_selling_price", columnList = "selling_price")
        })
public class Product extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, length = 60)
    private String sku;

    @Column(length = 80)
    private String brand;

    /** List price (MRP). DECIMAL avoids the rounding errors of double for money. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "discount_percent", nullable = false)
    private int discountPercent;

    /** price minus discount; stored so the catalog can filter and sort on it in SQL. */
    @Column(name = "selling_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    /** Average of all review ratings, kept up to date by ReviewService (so lists need no extra query). */
    @Column(name = "rating_average", nullable = false, precision = 3, scale = 2)
    private BigDecimal ratingAverage = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ProductImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ProductSpecification> specifications = new ArrayList<>();

    /**
     * Admin moderation. The column default keeps products that existed before this column was
     * added visible (they are treated as already approved).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20,
            columnDefinition = "varchar(20) not null default 'APPROVED'")
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    /** Units sold in paid, non-cancelled orders — used for "Best sellers" and popularity sorting. */
    @Column(name = "sold_count", nullable = false, columnDefinition = "int not null default 0")
    private int soldCount;

    public void replaceSpecifications(List<String[]> rows) {
        specifications.clear();
        int order = 0;
        for (String[] row : rows) {
            ProductSpecification spec = new ProductSpecification();
            spec.setProduct(this);
            spec.setName(row[0]);
            spec.setValue(row[1]);
            spec.setSortOrder(order++);
            specifications.add(spec);
        }
    }

    /** Replaces the gallery with the given URLs (max 5, in order). */
    public void replaceGallery(List<String> urls) {
        images.clear();
        int order = 0;
        for (String url : urls) {
            ProductImage image = new ProductImage();
            image.setProduct(this);
            image.setImageUrl(url);
            image.setSortOrder(order++);
            images.add(image);
        }
    }

    /** Sets price and discount together so selling_price can never get out of sync. */
    public void applyPricing(BigDecimal price, int discountPercent) {
        this.price = price.setScale(2, RoundingMode.HALF_UP);
        this.discountPercent = discountPercent;
        BigDecimal multiplier = BigDecimal.valueOf(100L - discountPercent)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        this.sellingPrice = this.price.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }
}
