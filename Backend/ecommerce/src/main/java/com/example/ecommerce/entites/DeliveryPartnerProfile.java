package com.example.ecommerce.entites;

import com.example.ecommerce.enums.VehicleType;

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
 * Onboarding details of a DELIVERY_PARTNER account. The application status itself is kept in
 * users.seller_status (PENDING / APPROVED / REJECTED / SUSPENDED), the same review flow as sellers.
 * Identity documents (driving licence, PAN) are checked in person at onboarding and never stored here.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "delivery_partner_profiles")
public class DeliveryPartnerProfile extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "delivery_partner_profile_id")
    private Long deliveryPartnerProfileId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    /** Indian registration number without spaces, e.g. MH12AB1234. Null for bicycles. */
    @Column(name = "vehicle_number", length = 15)
    private String vehicleNumber;

    @Column(name = "service_city", nullable = false, length = 80)
    private String serviceCity;

    @Column(name = "service_pincode", nullable = false, length = 10)
    private String servicePincode;
}
