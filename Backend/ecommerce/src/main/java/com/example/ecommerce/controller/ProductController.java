package com.example.ecommerce.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.product.ProductResponse;
import com.example.ecommerce.dtos.product.ProductSuggestion;
import com.example.ecommerce.dtos.review.ReviewResponse;
import com.example.ecommerce.dtos.review.ReviewSummary;
import com.example.ecommerce.services.ProductService;
import com.example.ecommerce.services.ProductService.CatalogQuery;
import com.example.ecommerce.services.ReviewService;

import lombok.RequiredArgsConstructor;

/** Public catalog. Filtering, sorting and paging all happen in the database. */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ReviewService reviewService;

    /**
     * GET /api/products?search=watch&categoryId=2&brand=Aura&brand=Pulse&minPrice=500&maxPrice=5000
     *     &inStock=true&minRating=4&minDiscount=10&sort=popular&page=0&size=12
     * sort: newest | price_asc | price_desc | discount | rating | popular | name
     */
    @GetMapping
    public PageResponse<ProductResponse> search(@RequestParam(required = false) String search,
                                                @RequestParam(required = false) Integer categoryId,
                                                @RequestParam(name = "brand", required = false) List<String> brands,
                                                @RequestParam(required = false) BigDecimal minPrice,
                                                @RequestParam(required = false) BigDecimal maxPrice,
                                                @RequestParam(required = false) Boolean inStock,
                                                @RequestParam(required = false) BigDecimal minRating,
                                                @RequestParam(required = false) Integer minDiscount,
                                                @RequestParam(required = false) String sort,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "12") int size) {
        return productService.searchPublic(new CatalogQuery(search, categoryId, brands, minPrice, maxPrice, inStock,
                minRating, minDiscount, sort, page, size));
    }

    @GetMapping("/suggestions")
    public List<ProductSuggestion> suggestions(@RequestParam("q") String query) {
        return productService.suggest(query);
    }

    /** Brands for the filter sidebar (only brands that currently have visible products). */
    @GetMapping("/brands")
    public List<String> brands(@RequestParam(required = false) Integer categoryId) {
        return productService.brands(categoryId);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return productService.getPublicProduct(id);
    }

    @GetMapping("/{id}/reviews")
    public PageResponse<ReviewResponse> reviews(@PathVariable Long id,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "5") int size) {
        return reviewService.productReviews(id, page, size);
    }

    @GetMapping("/{id}/reviews/summary")
    public ReviewSummary reviewSummary(@PathVariable Long id) {
        return reviewService.summary(id);
    }
}
