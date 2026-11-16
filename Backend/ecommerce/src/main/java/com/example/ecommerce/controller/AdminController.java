package com.example.ecommerce.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.dtos.admin.AdminDashboardResponse;
import com.example.ecommerce.dtos.admin.PlatformSettingsDto;
import com.example.ecommerce.dtos.admin.ReportDtos.AdminReport;
import com.example.ecommerce.dtos.admin.CreateAdminRequest;
import com.example.ecommerce.dtos.admin.SellerStatusRequest;
import com.example.ecommerce.dtos.delivery.DeliveryPartnerResponse;
import com.example.ecommerce.dtos.auth.UserResponse;
import com.example.ecommerce.dtos.common.ContactMessageResponse;
import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.common.StatusUpdateRequest;
import com.example.ecommerce.dtos.order.OrderResponse;
import com.example.ecommerce.dtos.order.OrderStatusUpdateRequest;
import com.example.ecommerce.dtos.order.OrderSummaryResponse;
import com.example.ecommerce.enums.OrderStatusGroup;
import com.example.ecommerce.dtos.order.PaymentResponse;
import com.example.ecommerce.dtos.product.AdminProductRequest;
import com.example.ecommerce.dtos.product.ProductCategoryRequest;
import com.example.ecommerce.dtos.product.ProductRequest;
import com.example.ecommerce.dtos.product.ProductResponse;
import com.example.ecommerce.dtos.product.ProductSummaryResponse;
import com.example.ecommerce.dtos.product.RejectProductRequest;
import com.example.ecommerce.dtos.returns.ReturnDecisionRequest;
import com.example.ecommerce.dtos.returns.ReturnResponse;
import com.example.ecommerce.dtos.review.ReviewResponse;
import com.example.ecommerce.enums.ApprovalStatus;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.enums.PaymentStatus;
import com.example.ecommerce.enums.ReturnStatus;
import com.example.ecommerce.enums.Role;
import com.example.ecommerce.enums.SellerStatus;
import com.example.ecommerce.security.UserPrincipal;
import com.example.ecommerce.services.ContactService;
import com.example.ecommerce.services.DeliveryPartnerService;
import com.example.ecommerce.services.DashboardService;
import com.example.ecommerce.services.OrderService;
import com.example.ecommerce.services.PaymentAdminService;
import com.example.ecommerce.enums.PaymentMethod;
import com.example.ecommerce.services.ProductService;
import com.example.ecommerce.services.ReportService;
import com.example.ecommerce.services.ReturnService;
import com.example.ecommerce.services.ReviewService;
import com.example.ecommerce.services.SettingsService;
import com.example.ecommerce.services.UserAdminService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** ADMIN only (enforced in SecurityConfig for /api/admin/**). */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final DashboardService dashboardService;
    private final UserAdminService userAdminService;
    private final ProductService productService;
    private final OrderService orderService;
    private final ReviewService reviewService;
    private final ReturnService returnService;
    private final ReportService reportService;
    private final SettingsService settingsService;
    private final ContactService contactService;
    private final DeliveryPartnerService deliveryPartnerService;
    private final PaymentAdminService paymentAdminService;

    @GetMapping("/dashboard")
    public AdminDashboardResponse dashboard() {
        return dashboardService.adminDashboard();
    }

    // ----------------------------------------------------------------- users

    @GetMapping("/users")
    public PageResponse<UserResponse> users(@RequestParam(required = false) Role role,
                                            @RequestParam(required = false) SellerStatus sellerStatus,
                                            @RequestParam(required = false) String search,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return userAdminService.listUsers(role, sellerStatus, search, page, size);
    }

    @PostMapping("/admins")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createAdmin(@AuthenticationPrincipal UserPrincipal admin, @Valid @RequestBody CreateAdminRequest request) {
        return userAdminService.createAdmin(admin.getId(), request);
    }

    @PatchMapping("/sellers/{id}/status")
    public UserResponse sellerStatus(@PathVariable Long id, @Valid @RequestBody SellerStatusRequest request) {
        return userAdminService.updateSellerStatus(id, request.status());
    }

    @PatchMapping("/users/{id}/status")
    public UserResponse userStatus(@AuthenticationPrincipal UserPrincipal admin, @PathVariable Long id,
                                   @Valid @RequestBody StatusUpdateRequest request) {
        return userAdminService.setUserActive(admin.getId(), id, request.active());
    }

    // -------------------------------------------------------------- products

    /**
     * Product dashboard. approval=PENDING gives the approval queue; active=true/false shows only active
     * or inactive (soft-deleted) products.
     */
    @GetMapping("/products")
    public PageResponse<ProductResponse> products(@RequestParam(required = false) String search,
                                                  @RequestParam(required = false) Integer categoryId,
                                                  @RequestParam(required = false) ApprovalStatus approval,
                                                  @RequestParam(required = false) Boolean active,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return productService.adminProducts(search, categoryId, approval, active, page, size);
    }

    @GetMapping("/products/summary")
    public ProductSummaryResponse productSummary() {
        return productService.adminSummary();
    }

    @GetMapping("/products/{id}")
    public ProductResponse product(@PathVariable Long id) {
        return productService.adminProduct(id);
    }

    /** Add a new product (admins only): name, description, price, category, SKU and inventory count. */
    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@Valid @RequestBody AdminProductRequest request) {
        return productService.adminCreateProduct(request);
    }

    /** Update product details: price, description, inventory count, category and more. */
    @PutMapping("/products/{id}")
    public ProductResponse updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.adminUpdateProduct(id, request);
    }

    /** Deactivate (soft delete) or reactivate: {"active": false|true}. */
    @PatchMapping("/products/{id}/status")
    public ProductResponse productStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return productService.adminSetActive(id, request.active());
    }

    @PatchMapping("/products/{id}/approve")
    public ProductResponse approveProduct(@PathVariable Long id) {
        return productService.approve(id);
    }

    @PatchMapping("/products/{id}/category")
    public ProductResponse moveProduct(@PathVariable Long id, @Valid @RequestBody ProductCategoryRequest request) {
        return productService.moveToCategory(id, request.categoryId());
    }

    @PatchMapping("/products/{id}/reject")
    public ProductResponse rejectProduct(@PathVariable Long id, @Valid @RequestBody RejectProductRequest request) {
        return productService.reject(id, request.reason());
    }

    // ---------------------------------------------------------------- orders

    /**
     * Order dashboard. group=PENDING|SHIPPED|DELIVERED|CANCELLED|RETURNS|UNPAID filters by status group;
     * status= filters by one exact status. Search matches order number, customer name or email, and city.
     */
    @GetMapping("/orders")
    public PageResponse<OrderResponse> orders(@RequestParam(required = false) OrderStatus status,
                                              @RequestParam(required = false) OrderStatusGroup group,
                                              @RequestParam(required = false) String search,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return orderService.adminOrders(status, group, search, page, size);
    }

    @GetMapping("/orders/summary")
    public OrderSummaryResponse orderSummary() {
        return orderService.adminOrderSummary();
    }

    @GetMapping("/orders/{id}")
    public OrderResponse order(@PathVariable Long id) {
        return orderService.adminOrder(id);
    }

    @PatchMapping("/orders/{id}/status")
    public OrderResponse orderStatus(@PathVariable Long id, @Valid @RequestBody OrderStatusUpdateRequest request) {
        return orderService.adminUpdateStatus(id, request.status(), request.note());
    }

    // ------------------------------------------------------ payments/returns

    /**
     * Payment dashboard (Payment Management module): status, refundedOnly and method (RAZORPAY|COD)
     * filters; search by order number, customer or Razorpay payment id.
     */
    @GetMapping("/payments")
    public PageResponse<PaymentResponse> payments(@RequestParam(required = false) PaymentStatus status,
                                                  @RequestParam(defaultValue = "false") boolean refundedOnly,
                                                  @RequestParam(required = false) PaymentMethod method,
                                                  @RequestParam(required = false) String search,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        return paymentAdminService.list(status, refundedOnly, method, search, page, size);
    }

    @GetMapping("/returns")
    public PageResponse<ReturnResponse> returns(@RequestParam(required = false) ReturnStatus status,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return returnService.adminReturns(status, page, size);
    }

    @PatchMapping("/returns/{id}")
    public ReturnResponse decideReturn(@PathVariable Long id, @Valid @RequestBody ReturnDecisionRequest request) {
        return returnService.decide(null, id, request.approve(), request.note());
    }

    // --------------------------------------------------------------- reviews

    @GetMapping("/reviews")
    public PageResponse<ReviewResponse> reviews(@RequestParam(required = false) Integer rating,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return reviewService.adminReviews(rating, page, size);
    }

    /** Moderation: remove a review that breaks the content policy. */
    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<Void> deleteReview(@PathVariable Long id) {
        reviewService.adminDelete(id);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------- reports and settings

    @GetMapping("/reports")
    public AdminReport reports(@RequestParam(defaultValue = "30") int days) {
        return reportService.adminReport(days);
    }

    @GetMapping("/settings")
    public PlatformSettingsDto settings() {
        return PlatformSettingsDto.from(settingsService.get());
    }

    @PutMapping("/settings")
    public PlatformSettingsDto updateSettings(@Valid @RequestBody PlatformSettingsDto request) {
        return settingsService.update(request);
    }

    // -------------------------------------------------------------- delivery partners

    @GetMapping("/delivery-partners")
    public PageResponse<DeliveryPartnerResponse> deliveryPartners(@RequestParam(required = false) SellerStatus status,
                                                                  @RequestParam(required = false) String search,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "10") int size) {
        return deliveryPartnerService.list(status, search, page, size);
    }

    @PatchMapping("/delivery-partners/{id}/status")
    public DeliveryPartnerResponse deliveryPartnerStatus(@PathVariable Long id, @Valid @RequestBody SellerStatusRequest request) {
        return deliveryPartnerService.updateStatus(id, request.status());
    }

    // -------------------------------------------------------------- messages

    @GetMapping("/messages")
    public PageResponse<ContactMessageResponse> messages(@RequestParam(required = false) Boolean resolved,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "10") int size) {
        return contactService.list(resolved, page, size);
    }

    @PatchMapping("/messages/{id}")
    public ContactMessageResponse resolveMessage(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return contactService.setResolved(id, request.active());
    }
}
