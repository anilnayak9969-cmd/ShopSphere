package com.example.ecommerce.config;

import java.math.BigDecimal;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.entites.Coupon;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.DiscountType;
import com.example.ecommerce.enums.Role;
import com.example.ecommerce.repositories.CouponRepository;
import com.example.ecommerce.repositories.UserRepository;
import com.example.ecommerce.services.SettingsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs once at startup, before {@link CatalogSeeder}.
 * 1. Creates the platform_settings row with defaults.
 * 2. Creates the first ADMIN from app.admin.email / app.admin.password. More admins are added by an
 *    existing admin from Admin → Users → Add admin (admins can never self-register).
 * 3. With app.seed.demo-data=true, adds the WELCOME10 coupon.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CouponRepository couponRepository;
    private final PasswordEncoder passwordEncoder;
    private final SettingsService settingsService;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Value("${app.seed.demo-data:false}")
    private boolean seedDemoData;

    @Override
    @Transactional
    public void run(String... args) {
        settingsService.get(); // creates the platform_settings row with defaults on first start
        createAdminIfMissing();
        if (seedDemoData && couponRepository.count() == 0) {
            seedDemoCoupon();
        }
    }

    /** WELCOME10: 10% off orders of ₹499 or more, capped at ₹500 — handy for testing checkout. */
    private void seedDemoCoupon() {
        Coupon coupon = new Coupon();
        coupon.setCode("WELCOME10");
        coupon.setDescription("10% off your order (up to ₹500) on orders above ₹499");
        coupon.setDiscountType(DiscountType.PERCENT);
        coupon.setDiscountValue(new BigDecimal("10"));
        coupon.setMinOrderAmount(new BigDecimal("499"));
        coupon.setMaxDiscount(new BigDecimal("500"));
        coupon.setActive(true);
        couponRepository.save(coupon);
        log.info("Seeded demo coupon WELCOME10");
    }

    private void createAdminIfMissing() {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.warn("No ADMIN account exists. Set app.admin.email and app.admin.password to create one.");
            return;
        }
        User admin = new User();
        admin.setFullName("ShopSphere Admin");
        admin.setEmail(adminEmail.trim().toLowerCase(Locale.ROOT));
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);
        admin.setEmailVerified(true);
        admin.setActive(true);
        userRepository.save(admin);
        log.info("Created ADMIN account {}", admin.getEmail());
    }
}
