package com.example.ecommerce.config;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.entites.Address;
import com.example.ecommerce.entites.Category;
import com.example.ecommerce.entites.DeliveryPartnerProfile;
import com.example.ecommerce.entites.Product;
import com.example.ecommerce.entites.SellerProfile;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.ApprovalStatus;
import com.example.ecommerce.enums.BusinessType;
import com.example.ecommerce.enums.Role;
import com.example.ecommerce.enums.SellerStatus;
import com.example.ecommerce.enums.VehicleType;
import com.example.ecommerce.repositories.AddressRepository;
import com.example.ecommerce.repositories.CategoryRepository;
import com.example.ecommerce.repositories.DeliveryPartnerProfileRepository;
import com.example.ecommerce.repositories.ProductRepository;
import com.example.ecommerce.repositories.SellerProfileRepository;
import com.example.ecommerce.repositories.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads the starter catalogue (src/main/resources/seed/catalog.json) and the demo accounts when
 * app.seed.demo-data=true:
 * <ul>
 *   <li>stores (seller accounts + store profiles), departments and subcategories, products with
 *       descriptions, specifications and photos — created if missing, never overwritten once they exist
 *       (except old "DEMO-" products, which are upgraded in place through their {@code replaces} SKU);</li>
 *   <li>one demo account per self-service role (customer, seller, delivery partner) whose password is
 *       reset to app.demo.password on every start, so the "Try a demo account" buttons always work.</li>
 * </ul>
 * Set app.seed.demo-data=false in production.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class CatalogSeeder implements CommandLineRunner {

    public static final String DEMO_CUSTOMER = "demo.customer@shopsphere.local";
    public static final String DEMO_SELLER = "demo.seller@shopsphere.local";
    public static final String DEMO_PARTNER = "demo.rider@shopsphere.local";

    private static final String UNSPLASH = "https://images.unsplash.com/%s?w=900&q=80";

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final DeliveryPartnerProfileRepository deliveryPartnerProfileRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;
    private final JsonMapper jsonMapper;

    @Value("${app.seed.demo-data:false}")
    private boolean seedDemoData;

    /** One password for all demo accounts. Falls back to the older app.demo.seller-password property. */
    @Value("${app.demo.password:${app.demo.seller-password:}}")
    private String demoPassword;

    // ------------------------------------------------------------------ JSON shape

    record SeedCatalog(List<String> retiredImages, List<SeedSeller> sellers, List<SeedDepartment> departments,
                       List<SeedProduct> products) {
    }

    record SeedSeller(String key, String email, String fullName, String phone, String storeName, String businessType,
                      String description, String address, String city, String state, String pincode) {
    }

    record SeedDepartment(String name, String description, String image, List<SeedCategory> subcategories) {
    }

    record SeedCategory(String name, String description, String image) {
    }

    record SeedProduct(String sku, String replaces, String seller, String category, String name, String brand,
                       BigDecimal price, int discount, int stock, String image, List<String> gallery,
                       String description, List<List<String>> specs) {
    }

    // ------------------------------------------------------------------ run

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (!seedDemoData) {
            return;
        }
        SeedCatalog catalog;
        try (InputStream in = new ClassPathResource("seed/catalog.json").getInputStream()) {
            catalog = jsonMapper.readValue(in, SeedCatalog.class);
        }
        Set<String> retired = Set.copyOf(catalog.retiredImages().stream().map(CatalogSeeder::imageUrl).toList());

        Map<String, User> sellers = new HashMap<>();
        for (SeedSeller s : catalog.sellers()) {
            sellers.put(s.key(), upsertSeller(s));
        }

        Map<String, Category> categories = new HashMap<>();
        categoryRepository.findAll().forEach(c -> categories.put(key(c.getCategoryName()), c));
        for (SeedDepartment d : catalog.departments()) {
            Category department = upsertCategory(categories, d.name(), d.description(), d.image(), null, retired);
            for (SeedCategory sub : d.subcategories()) {
                upsertCategory(categories, sub.name(), sub.description(), sub.image(), department, retired);
            }
        }

        int created = 0;
        int upgraded = 0;
        for (SeedProduct p : catalog.products()) {
            if (productRepository.findBySkuIgnoreCase(p.sku()).isPresent()) {
                continue; // already seeded — keep any edits made by the seller or admin
            }
            Product product = p.replaces() == null ? null : productRepository.findBySkuIgnoreCase(p.replaces()).orElse(null);
            if (product == null) {
                product = new Product();
                created++;
            } else {
                upgraded++;
            }
            fillProduct(product, p, sellers.get(p.seller()), categories.get(key(p.category())));
            productRepository.save(product);
        }

        seedDemoCustomer();
        seedDemoPartner();
        log.info("Catalogue seed: {} stores, {} categories, {} products created, {} demo products upgraded",
                sellers.size(), categories.size(), created, upgraded);
    }

    // ------------------------------------------------------------------ stores

    private User upsertSeller(SeedSeller s) {
        User seller = userRepository.findByEmailIgnoreCase(s.email()).orElse(null);
        boolean isNew = seller == null;
        if (isNew) {
            seller = new User();
            seller.setEmail(s.email().toLowerCase(Locale.ROOT));
            seller.setRole(Role.SELLER);
            seller.setEmailVerified(true);
            seller.setActive(true);
            seller.setSellerStatus(SellerStatus.APPROVED);
            seller.setPassword(passwordEncoder.encode(demoPasswordOrRandom()));
        } else if (s.email().toLowerCase(Locale.ROOT).endsWith("@shopsphere.local")) {
            // Catalogue stores are demo accounts: keep their "Try a demo account" logins working.
            seller.setPassword(passwordEncoder.encode(demoPasswordOrRandom()));
        }
        // The original demo store is renamed to its catalogue store; other existing sellers keep their details.
        if (isNew || "ShopSphere Demo Store".equals(seller.getStoreName())) {
            seller.setFullName(s.fullName());
            seller.setPhone(s.phone());
            seller.setStoreName(s.storeName());
        }
        User saved = userRepository.save(seller);

        if (sellerProfileRepository.findByUserId(saved.getId()).isEmpty()) {
            SellerProfile profile = new SellerProfile();
            profile.setUser(saved);
            profile.setBusinessType(BusinessType.valueOf(s.businessType()));
            profile.setDescription(s.description());
            profile.setBusinessAddress(s.address());
            profile.setCity(s.city());
            profile.setState(s.state());
            profile.setPincode(s.pincode());
            profile.setSupportEmail(saved.getEmail());
            profile.setSupportPhone(saved.getPhone());
            sellerProfileRepository.save(profile);
        }
        return saved;
    }

    // ------------------------------------------------------------------ categories

    private Category upsertCategory(Map<String, Category> byName, String name, String description, String image,
                                    Category parent, Set<String> retired) {
        Category category = byName.get(key(name));
        if (category == null) {
            category = new Category();
            category.setCategoryName(name);
            category.setDescription(description);
            category.setStatus(true);
        }
        // Fill a missing picture, or replace a retired one; an image chosen by an admin is kept.
        if (category.getImageUrl() == null || retired.contains(category.getImageUrl())) {
            category.setImageUrl(imageUrl(image));
        }
        if (parent != null && category.getParent() == null) {
            category.setParent(parent);
        }
        Category saved = categoryRepository.save(category);
        byName.put(key(name), saved);
        return saved;
    }

    // ------------------------------------------------------------------ products

    private void fillProduct(Product product, SeedProduct p, User seller, Category category) {
        if (seller == null || category == null) {
            throw new IllegalStateException("catalog.json: unknown seller or category for " + p.sku());
        }
        product.setSku(p.sku());
        product.setProductName(p.name());
        product.setBrand(p.brand());
        product.setDescription(p.description());
        product.setCategory(category);
        product.setSeller(seller);
        product.applyPricing(p.price(), p.discount());
        product.setStockQuantity(p.stock());
        product.setImageUrl(imageUrl(p.image()));
        product.replaceGallery(p.gallery() == null ? List.of() : p.gallery().stream().map(CatalogSeeder::imageUrl).toList());
        product.replaceSpecifications(p.specs() == null ? List.of()
                : p.specs().stream().map(row -> new String[]{row.get(0), row.get(1)}).toList());
        product.setActive(true);
        product.setApprovalStatus(ApprovalStatus.APPROVED);
        product.setRejectionReason(null);
    }

    // ------------------------------------------------------------------ demo accounts

    private void seedDemoCustomer() {
        User customer = userRepository.findByEmailIgnoreCase(DEMO_CUSTOMER).orElseGet(() -> {
            User u = new User();
            u.setEmail(DEMO_CUSTOMER);
            u.setFullName("Priya Sharma");
            u.setPhone("9000000010");
            u.setRole(Role.CUSTOMER);
            u.setEmailVerified(true);
            u.setActive(true);
            return u;
        });
        customer.setPassword(passwordEncoder.encode(demoPasswordOrRandom()));
        User saved = userRepository.save(customer);
        if (addressRepository.countByUserId(saved.getId()) == 0) {
            Address home = new Address();
            home.setUser(saved);
            home.setLabel("Home");
            home.setFullName(saved.getFullName());
            home.setPhone(saved.getPhone());
            home.setAddressLine1("Flat 302, Lotus Residency");
            home.setAddressLine2("Kalyani Nagar");
            home.setCity("Pune");
            home.setState("Maharashtra");
            home.setPincode("411006");
            home.setDefaultAddress(true);
            addressRepository.save(home);
        }
    }

    private void seedDemoPartner() {
        User partner = userRepository.findByEmailIgnoreCase(DEMO_PARTNER).orElseGet(() -> {
            User u = new User();
            u.setEmail(DEMO_PARTNER);
            u.setFullName("Ravi Kumar");
            u.setPhone("9000000011");
            u.setRole(Role.DELIVERY_PARTNER);
            u.setSellerStatus(SellerStatus.APPROVED);
            u.setEmailVerified(true);
            u.setActive(true);
            return u;
        });
        partner.setPassword(passwordEncoder.encode(demoPasswordOrRandom()));
        User saved = userRepository.save(partner);
        if (deliveryPartnerProfileRepository.findByUserId(saved.getId()).isEmpty()) {
            DeliveryPartnerProfile profile = new DeliveryPartnerProfile();
            profile.setUser(saved);
            profile.setVehicleType(VehicleType.SCOOTER);
            profile.setVehicleNumber("MH12ZZ0001");
            profile.setServiceCity("Pune");
            profile.setServicePincode("411006");
            deliveryPartnerProfileRepository.save(profile);
        }
    }

    // ------------------------------------------------------------------ helpers

    private String demoPasswordOrRandom() {
        return demoPassword == null || demoPassword.isBlank() ? UUID.randomUUID().toString() : demoPassword;
    }

    static String imageUrl(String image) {
        return image.startsWith("photo-") ? String.format(UNSPLASH, image) : image;
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
