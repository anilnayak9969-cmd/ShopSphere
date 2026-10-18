package com.example.ecommerce.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

import com.example.ecommerce.dtos.account.SellerProfileRequest;
import com.example.ecommerce.dtos.account.SellerProfileResponse;
import com.example.ecommerce.dtos.admin.ReportDtos.CommissionSummary;
import com.example.ecommerce.dtos.admin.ReportDtos.SellerReport;
import com.example.ecommerce.dtos.admin.SellerDashboardResponse;
import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.common.StatusUpdateRequest;
import com.example.ecommerce.dtos.order.FulfilmentUpdateRequest;
import com.example.ecommerce.dtos.order.SellerOrderItemResponse;
import com.example.ecommerce.dtos.product.ProductRequest;
import com.example.ecommerce.dtos.product.ProductResponse;
import com.example.ecommerce.dtos.product.StockUpdateRequest;
import com.example.ecommerce.dtos.returns.ReturnDecisionRequest;
import com.example.ecommerce.dtos.returns.ReturnResponse;
import com.example.ecommerce.enums.ApprovalStatus;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.enums.ReturnStatus;
import com.example.ecommerce.security.UserPrincipal;
import com.example.ecommerce.services.DashboardService;
import com.example.ecommerce.services.OrderService;
import com.example.ecommerce.services.ProductService;
import com.example.ecommerce.services.ReportService;
import com.example.ecommerce.services.ReturnService;
import com.example.ecommerce.services.SellerProfileService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** SELLER only. The seller id always comes from the token, so a seller can only touch their own data. */
@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
public class SellerController {

    private final ProductService productService;
    private final OrderService orderService;
    private final DashboardService dashboardService;
    private final SellerProfileService profileService;
    private final ReturnService returnService;
    private final ReportService reportService;

    @GetMapping("/dashboard")
    public SellerDashboardResponse dashboard(@AuthenticationPrincipal UserPrincipal seller) {
        return dashboardService.sellerDashboard(seller.getId());
    }

    // ------------------------------------------------------------- profile

    @GetMapping("/profile")
    public SellerProfileResponse profile(@AuthenticationPrincipal UserPrincipal seller) {
        return profileService.get(seller.getId());
    }

    @PutMapping("/profile")
    public SellerProfileResponse updateProfile(@AuthenticationPrincipal UserPrincipal seller,
                                               @Valid @RequestBody SellerProfileRequest request) {
        return profileService.update(seller.getId(), request);
    }

    // ------------------------------------------------------------ products

    /** maxStock turns this into the inventory / low-stock view (sorted by stock, lowest first). */
    @GetMapping("/products")
    public PageResponse<ProductResponse> products(@AuthenticationPrincipal UserPrincipal seller,
                                                 @RequestParam(required = false) String search,
                                                 @RequestParam(required = false) ApprovalStatus approval,
                                                 @RequestParam(required = false) Integer maxStock,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return productService.sellerProducts(seller.getId(), search, approval, maxStock, page, size);
    }

    @GetMapping("/products/{id}")
    public ProductResponse product(@AuthenticationPrincipal UserPrincipal seller, @PathVariable Long id) {
        return productService.sellerProduct(seller.getId(), id);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@AuthenticationPrincipal UserPrincipal seller,
                                  @Valid @RequestBody ProductRequest request) {
        return productService.createProduct(seller.getId(), request);
    }

    @PutMapping("/products/{id}")
    public ProductResponse update(@AuthenticationPrincipal UserPrincipal seller, @PathVariable Long id,
                                  @Valid @RequestBody ProductRequest request) {
        return productService.updateProduct(seller.getId(), id, request);
    }

    @PatchMapping("/products/{id}/status")
    public ProductResponse status(@AuthenticationPrincipal UserPrincipal seller, @PathVariable Long id,
                                  @Valid @RequestBody StatusUpdateRequest request) {
        return productService.setSellerProductActive(seller.getId(), id, request.active());
    }

    @PatchMapping("/products/{id}/stock")
    public ProductResponse stock(@AuthenticationPrincipal UserPrincipal seller, @PathVariable Long id,
                                 @Valid @RequestBody StockUpdateRequest request) {
        return productService.updateStock(seller.getId(), id, request.stockQuantity());
    }

    // -------------------------------------------------------------- orders

    @GetMapping("/orders")
    public PageResponse<SellerOrderItemResponse> orders(@AuthenticationPrincipal UserPrincipal seller,
                                                        @RequestParam(required = false) OrderStatus status,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "10") int size) {
        return orderService.sellerOrderItems(seller.getId(), status, page, size);
    }

    /** Confirm, pack, ship (courier + tracking required), mark delivered, or cancel one of your lines. */
    @PatchMapping("/order-items/{id}/status")
    public SellerOrderItemResponse updateItem(@AuthenticationPrincipal UserPrincipal seller, @PathVariable Long id,
                                              @Valid @RequestBody FulfilmentUpdateRequest request) {
        return orderService.updateSellerItem(seller.getId(), id, request);
    }

    // ------------------------------------------------------------- returns

    @GetMapping("/returns")
    public PageResponse<ReturnResponse> returns(@AuthenticationPrincipal UserPrincipal seller,
                                                @RequestParam(required = false) ReturnStatus status,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return returnService.sellerReturns(seller.getId(), status, page, size);
    }

    @PatchMapping("/returns/{id}")
    public ReturnResponse decideReturn(@AuthenticationPrincipal UserPrincipal seller, @PathVariable Long id,
                                       @Valid @RequestBody ReturnDecisionRequest request) {
        return returnService.decide(seller.getId(), id, request.approve(), request.note());
    }

    // ------------------------------------------------------------- reports

    @GetMapping("/reports/sales")
    public SellerReport sales(@AuthenticationPrincipal UserPrincipal seller,
                              @RequestParam(defaultValue = "30") int days) {
        return reportService.sellerReport(seller.getId(), days);
    }

    @GetMapping("/reports/commission")
    public CommissionSummary commission(@AuthenticationPrincipal UserPrincipal seller) {
        return reportService.commission(seller.getId());
    }
}
