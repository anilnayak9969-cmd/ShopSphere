package com.example.ecommerce.entites;

import com.example.ecommerce.enums.Role;
import com.example.ecommerce.enums.SellerStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
public class User extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 15)
    private String phone;

    /** BCrypt hash — never the plain password. */
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    /** Becomes true once the registration OTP is verified. Unverified users cannot log in. */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    /** False when an admin suspends the account. */
    @Column(nullable = false)
    private boolean active = true;

    /** Only used for sellers; null for every other role. */
    @Enumerated(EnumType.STRING)
    @Column(name = "seller_status", length = 20)
    private SellerStatus sellerStatus;

    @Column(name = "store_name", length = 120)
    private String storeName;
}
