package com.example.ecommerce.entites;

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

/**
 * Matches the assignment's "categories" table:
 * category_id, category_name, description, created_at, updated_at, status.
 * image_url is an extra optional column used by the storefront.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "categories",
        uniqueConstraints = @UniqueConstraint(name = "uk_categories_name", columnNames = "category_name"))
public class Category extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Integer categoryId;

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(length = 300)
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** true = active, false = inactive (soft deleted). Rows are never physically deleted. */
    @Column(nullable = false)
    private boolean status = true;

    /** Optional parent for subcategories, e.g. Electronics > Mobiles. Null = top-level category. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_category_id")
    private Category parent;
}
