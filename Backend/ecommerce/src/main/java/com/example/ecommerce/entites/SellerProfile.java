package com.example.ecommerce.entites;

import com.example.ecommerce.enums.BusinessType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Store details shown to customers and used for invoices. The store name itself stays on users.store_name
 * so existing queries keep working; everything else about the business lives here.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "seller_profiles")
public class SellerProfile extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seller_profile_id")
    private Long sellerProfileId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(length = 1000)
    private String description;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "support_email", length = 150)
    private String supportEmail;

    @Column(name = "support_phone", length = 15)
    private String supportPhone;

    /** Asked at registration; null for sellers who registered before this field existed. */
    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", length = 20)
    private BusinessType businessType;

    /** Indian GST registration number (optional for small sellers). */
    @Column(length = 15)
    private String gstin;

    @Column(name = "business_address", length = 255)
    private String businessAddress;

    @Column(length = 80)
    private String city;

    @Column(length = 80)
    private String state;

    @Column(length = 10)
    private String pincode;
}
