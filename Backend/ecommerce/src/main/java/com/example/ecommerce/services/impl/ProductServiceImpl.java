package com.example.ecommerce.services.impl;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.product.ProductRequest;
import com.example.ecommerce.dtos.product.ProductResponse;
import com.example.ecommerce.dtos.product.ProductSuggestion;
import com.example.ecommerce.dtos.product.SpecificationDto;
import com.example.ecommerce.entites.Category;
import com.example.ecommerce.entites.Product;
import com.example.ecommerce.entites.ProductImage;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.ApprovalStatus;
import com.example.ecommerce.enums.SellerStatus;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.CategoryRepository;
import com.example.ecommerce.repositories.ProductRepository;
import com.example.ecommerce.repositories.ProductSpecifications;
import com.example.ecommerce.repositories.ProductSpecifications.Filter;
import com.example.ecommerce.repositories.UserRepository;
import com.example.ecommerce.services.CategoryService;
import com.example.ecommerce.services.NotificationService;
import com.example.ecommerce.services.ProductService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final int MAX_PAGE_SIZE = 48;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final CategoryService categoryService;
    private final NotificationService notificationService;

    // ------------------------------------------------------------ storefront

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> searchPublic(CatalogQuery q) {
        if (q.minPrice() != null && q.maxPrice() != null && q.minPrice().compareTo(q.maxPrice()) > 0) {
            throw ApiException.badRequest("Minimum price cannot be greater than maximum price.");
        }
        Set<Integer> categoryIds = q.categoryId() == null ? null : categoryService.withDescendants(q.categoryId());
        Filter filter = Filter.publicCatalog(q.search(), categoryIds, q.brands(), q.minPrice(), q.maxPrice(),
                q.inStock(), q.minRating(), q.minDiscount());
        PageRequest pageable = PageRequest.of(Math.max(q.page(), 0), clampSize(q.size()), toSort(q.sort()));
        return PageResponse.of(productRepository.findAll(ProductSpecifications.filter(filter), pageable),
                ProductResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getPublicProduct(Long productId) {
        Product product = productRepository.findWithDetailsByProductId(productId)
                .orElseThrow(() -> ApiException.notFound("This product does not exist."));
        if (!isPubliclyVisible(product)) {
            String reason = product.getApprovalStatus() != ApprovalStatus.APPROVED
                    ? "This product is awaiting approval and is not on sale yet."
                    : "This product is currently unavailable.";
            throw new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "PRODUCT_UNAVAILABLE", reason);
        }
        return ProductResponse.detailed(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductSuggestion> suggest(String query) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }
        return productRepository.suggest(query.trim(), PageRequest.of(0, 6)).stream()
                .map(p -> new ProductSuggestion(p.getProductId(), p.getProductName(), p.getCategory().getCategoryName()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> brands(Integer categoryId) {
        return categoryId == null ? productRepository.visibleBrands()
                : productRepository.visibleBrandsIn(categoryService.withDescendants(categoryId));
    }

    // ---------------------------------------------------------------- seller

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> sellerProducts(Long sellerId, String search, ApprovalStatus approval,
                                                        Integer maxStock, int page, int size) {
        Sort sort = maxStock != null ? Sort.by("stockQuantity").ascending() : Sort.by("createdAt").descending();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), clampSize(size), sort);
        return PageResponse.of(productRepository.findAll(
                ProductSpecifications.filter(Filter.forSeller(sellerId, search, approval, maxStock)), pageable),
                ProductResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse sellerProduct(Long sellerId, Long productId) {
        return ProductResponse.detailed(findOwned(sellerId, productId));
    }

    @Override
    @Transactional
    public ProductResponse createProduct(Long sellerId, ProductRequest request) {
        User seller = requireApprovedSeller(sellerId);
        Category category = requireActiveCategory(request.categoryId());
        String sku = request.sku().trim().toUpperCase();
        if (productRepository.existsBySkuIgnoreCase(sku)) {
            throw ApiException.conflict("DUPLICATE_SKU", "SKU " + sku + " is already in use.");
        }
        Product product = new Product();
        product.setSeller(seller);
        product.setActive(true);
        product.setApprovalStatus(ApprovalStatus.PENDING);
        apply(product, request, category, sku);
        Product saved = productRepository.save(product);
        notificationService.notifyAdmins("Product awaiting approval",
                seller.getStoreName() + " listed \"" + saved.getProductName() + "\".", "/admin/products?approval=PENDING");
        return ProductResponse.detailed(saved);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long sellerId, Long productId, ProductRequest request) {
        User seller = requireApprovedSeller(sellerId);
        Product product = findOwned(sellerId, productId);
        Category category = product.getCategory().getCategoryId().equals(request.categoryId())
                ? product.getCategory()
                : requireActiveCategory(request.categoryId());
        String sku = request.sku().trim().toUpperCase();
        if (productRepository.existsBySkuIgnoreCaseAndProductIdNot(sku, productId)) {
            throw ApiException.conflict("DUPLICATE_SKU", "SKU " + sku + " is already in use.");
        }
        // Changes to what customers read or see need a fresh review; price and stock changes do not.
        boolean contentChanged = contentChanged(product, request, category);
        apply(product, request, category, sku);
        if (contentChanged && product.getApprovalStatus() != ApprovalStatus.PENDING) {
            product.setApprovalStatus(ApprovalStatus.PENDING);
            product.setRejectionReason(null);
            notificationService.notifyAdmins("Product updated — review needed",
                    seller.getStoreName() + " changed \"" + product.getProductName() + "\".", "/admin/products?approval=PENDING");
        }
        return ProductResponse.detailed(productRepository.saveAndFlush(product));
    }

    @Override
    @Transactional
    public ProductResponse setSellerProductActive(Long sellerId, Long productId, boolean active) {
        Product product = findOwned(sellerId, productId);
        if (active) {
            requireApprovedSeller(sellerId);
            if (!product.getCategory().isStatus()) {
                throw ApiException.badRequest("CATEGORY_INACTIVE",
                        "This product's category is inactive. Move it to an active category first.");
            }
        }
        product.setActive(active);
        return ProductResponse.from(productRepository.saveAndFlush(product));
    }

    @Override
    @Transactional
    public ProductResponse updateStock(Long sellerId, Long productId, int stockQuantity) {
        Product product = findOwned(sellerId, productId);
        product.setStockQuantity(stockQuantity);
        return ProductResponse.from(productRepository.saveAndFlush(product));
    }

    // ----------------------------------------------------------------- admin

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> adminProducts(String search, Integer categoryId, ApprovalStatus approval,
                                                       int page, int size) {
        Set<Integer> categoryIds = categoryId == null ? null : categoryService.withDescendants(categoryId);
        Sort sort = approval == ApprovalStatus.PENDING ? Sort.by("updatedAt").ascending() : Sort.by("createdAt").descending();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), clampSize(size), sort);
        return PageResponse.of(productRepository.findAll(
                ProductSpecifications.filter(Filter.forAdmin(search, categoryIds, approval)), pageable),
                ProductResponse::from);
    }

    @Override
    @Transactional
    public ProductResponse adminSetActive(Long productId, boolean active) {
        Product product = findAny(productId);
        product.setActive(active);
        return ProductResponse.from(productRepository.saveAndFlush(product));
    }

    @Override
    @Transactional
    public ProductResponse approve(Long productId) {
        Product product = findAny(productId);
        product.setApprovalStatus(ApprovalStatus.APPROVED);
        product.setRejectionReason(null);
        notificationService.notify(product.getSeller(), "Product approved",
                "\"" + product.getProductName() + "\" is now live in the store.", "/seller/products");
        return ProductResponse.from(productRepository.saveAndFlush(product));
    }

    @Override
    @Transactional
    public ProductResponse moveToCategory(Long productId, Integer categoryId) {
        Product product = findAny(productId);
        if (product.getCategory().getCategoryId().equals(categoryId)) {
            throw ApiException.badRequest("SAME_CATEGORY", "This product is already in that category.");
        }
        Category category = requireActiveCategory(categoryId);
        product.setCategory(category);
        notificationService.notify(product.getSeller(), "Product moved to a new category",
                "The ShopSphere team moved \"" + product.getProductName() + "\" to " + category.getCategoryName() + ".",
                "/seller/products/" + product.getProductId() + "/edit");
        return ProductResponse.from(productRepository.saveAndFlush(product));
    }

    @Override
    @Transactional
    public ProductResponse reject(Long productId, String reason) {
        Product product = findAny(productId);
        product.setApprovalStatus(ApprovalStatus.REJECTED);
        product.setRejectionReason(reason.trim());
        notificationService.notify(product.getSeller(), "Product needs changes",
                "\"" + product.getProductName() + "\" was not approved: " + reason.trim(),
                "/seller/products/" + product.getProductId() + "/edit");
        return ProductResponse.from(productRepository.saveAndFlush(product));
    }

    // --------------------------------------------------------------- helpers

    /** Looking up by id AND seller id means another seller's product simply "does not exist". */
    private Product findOwned(Long sellerId, Long productId) {
        return productRepository.findByProductIdAndSellerId(productId, sellerId)
                .orElseThrow(() -> ApiException.notFound("Product " + productId + " was not found in your store."));
    }

    private Product findAny(Long productId) {
        return productRepository.findWithDetailsByProductId(productId)
                .orElseThrow(() -> ApiException.notFound("Product " + productId + " was not found."));
    }

    private User requireApprovedSeller(Long sellerId) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() -> ApiException.notFound("Seller account not found."));
        if (seller.getSellerStatus() != SellerStatus.APPROVED || !seller.isActive()) {
            throw ApiException.forbidden("SELLER_NOT_APPROVED",
                    "Your store must be approved by an administrator before you can list products.");
        }
        return seller;
    }

    private Category requireActiveCategory(Integer categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> ApiException.badRequest("CATEGORY_NOT_FOUND", "The selected category does not exist."));
        if (!category.isStatus()) {
            throw ApiException.badRequest("CATEGORY_INACTIVE", "The selected category is inactive.");
        }
        return category;
    }

    private static boolean contentChanged(Product p, ProductRequest r, Category category) {
        List<String> newGallery = cleanGallery(r);
        List<String> oldGallery = p.getImages().stream().map(ProductImage::getImageUrl).toList();
        List<SpecificationDto> oldSpecs = p.getSpecifications().stream()
                .map(s -> new SpecificationDto(s.getName(), s.getValue())).toList();
        return !p.getProductName().equals(r.productName().trim())
                || !Objects.equals(p.getDescription(), r.description().trim())
                || !Objects.equals(p.getBrand(), blankToNull(r.brand()))
                || !Objects.equals(p.getImageUrl(), blankToNull(r.imageUrl()))
                || !p.getCategory().getCategoryId().equals(category.getCategoryId())
                || !oldGallery.equals(newGallery)
                || !oldSpecs.equals(cleanSpecs(r));
    }

    private void apply(Product product, ProductRequest request, Category category, String sku) {
        product.setProductName(request.productName().trim());
        product.setDescription(request.description().trim());
        product.setSku(sku);
        product.setBrand(blankToNull(request.brand()));
        product.applyPricing(request.price(), request.discountPercent());
        product.setStockQuantity(request.stockQuantity());
        product.setImageUrl(blankToNull(request.imageUrl()));
        product.setCategory(category);
        product.replaceGallery(cleanGallery(request));
        product.replaceSpecifications(cleanSpecs(request).stream().map(s -> new String[]{s.name(), s.value()}).toList());
    }

    private static List<String> cleanGallery(ProductRequest r) {
        return r.galleryImageUrls() == null ? List.of() : r.galleryImageUrls().stream()
                .filter(url -> url != null && !url.isBlank()).map(String::trim).distinct().limit(5).toList();
    }

    private static List<SpecificationDto> cleanSpecs(ProductRequest r) {
        return r.specifications() == null ? List.of() : r.specifications().stream()
                .map(s -> new SpecificationDto(s.name().trim(), s.value().trim())).limit(20).toList();
    }

    /** The single rule for "can a customer see and buy this product?". */
    static boolean isPubliclyVisible(Product p) {
        return p.isActive() && p.getApprovalStatus() == ApprovalStatus.APPROVED && p.getCategory().isStatus()
                && p.getSeller().isActive() && p.getSeller().getSellerStatus() == SellerStatus.APPROVED;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    private static Sort toSort(String sort) {
        if (sort == null) {
            return Sort.by("createdAt").descending();
        }
        return switch (sort) {
            case "price_asc" -> Sort.by("sellingPrice").ascending();
            case "price_desc" -> Sort.by("sellingPrice").descending();
            case "discount" -> Sort.by("discountPercent").descending().and(Sort.by("soldCount").descending());
            case "rating" -> Sort.by("ratingAverage").descending().and(Sort.by("ratingCount").descending());
            case "popular" -> Sort.by("soldCount").descending().and(Sort.by("ratingCount").descending());
            case "name" -> Sort.by("productName").ascending();
            default -> Sort.by("createdAt").descending();
        };
    }
}
