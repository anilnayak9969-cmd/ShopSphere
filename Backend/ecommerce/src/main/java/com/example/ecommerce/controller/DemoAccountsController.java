package com.example.ecommerce.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.config.CatalogSeeder;
import com.example.ecommerce.enums.Role;

/**
 * Demo logins shown as "Try a demo account" buttons on the sign-in page. Returns an empty list unless
 * demo seeding is on, so nothing is exposed in production (app.seed.demo-data=false). The admin demo is
 * listed only when app.demo.show-admin-login=true.
 */
@RestController
@RequestMapping("/api/public")
public class DemoAccountsController {

    public record DemoAccount(Role role, String label, String email, String password) {
    }

    @Value("${app.seed.demo-data:false}")
    private boolean seedDemoData;

    @Value("${app.demo.password:${app.demo.seller-password:}}")
    private String demoPassword;

    @Value("${app.demo.show-admin-login:false}")
    private boolean showAdminLogin;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @GetMapping("/demo-accounts")
    public List<DemoAccount> demoAccounts() {
        List<DemoAccount> accounts = new ArrayList<>();
        if (!seedDemoData || demoPassword == null || demoPassword.isBlank()) {
            return accounts;
        }
        accounts.add(new DemoAccount(Role.CUSTOMER, "Demo customer", CatalogSeeder.DEMO_CUSTOMER, demoPassword));
        accounts.add(new DemoAccount(Role.SELLER, "Demo seller (TechTrove Electronics)", CatalogSeeder.DEMO_SELLER, demoPassword));
        accounts.add(new DemoAccount(Role.DELIVERY_PARTNER, "Demo delivery partner", CatalogSeeder.DEMO_PARTNER, demoPassword));
        // The other catalogue stores, so an order for any product can be fulfilled by its own seller.
        accounts.add(new DemoAccount(Role.SELLER, "Thread & Loom (fashion)", "threadloom@shopsphere.local", demoPassword));
        accounts.add(new DemoAccount(Role.SELLER, "UrbanNest Home", "urbannest@shopsphere.local", demoPassword));
        accounts.add(new DemoAccount(Role.SELLER, "GlowBar Beauty", "glowbar@shopsphere.local", demoPassword));
        accounts.add(new DemoAccount(Role.SELLER, "PageTurner Books", "pageturner@shopsphere.local", demoPassword));
        accounts.add(new DemoAccount(Role.SELLER, "ActivePeak Sports", "activepeak@shopsphere.local", demoPassword));
        if (showAdminLogin && !adminEmail.isBlank() && !adminPassword.isBlank()) {
            accounts.add(new DemoAccount(Role.ADMIN, "Admin", adminEmail.trim().toLowerCase(), adminPassword));
        }
        return accounts;
    }
}
