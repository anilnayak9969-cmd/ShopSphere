package com.example.ecommerce.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.dtos.admin.PlatformSettingsDto;
import com.example.ecommerce.dtos.common.ContactRequest;
import com.example.ecommerce.dtos.common.MessageResponse;
import com.example.ecommerce.dtos.coupon.PublicCouponResponse;
import com.example.ecommerce.repositories.CouponRepository;
import com.example.ecommerce.services.ContactService;
import com.example.ecommerce.services.FileStorageService;
import com.example.ecommerce.services.SettingsService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Endpoints anyone may call: store settings, live promotions, the contact form and uploaded images. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PublicController {

    private final SettingsService settingsService;
    private final CouponRepository couponRepository;
    private final ContactService contactService;
    private final FileStorageService fileStorageService;

    /** Shipping fee, free-shipping threshold, COD availability and return window (no commission rate). */
    @GetMapping("/public/settings")
    public PlatformSettingsDto settings() {
        return PlatformSettingsDto.from(settingsService.get()).withoutCommission();
    }

    /** Coupons that are active right now — the home page's promotional banner uses these. */
    @GetMapping("/public/coupons")
    public List<PublicCouponResponse> coupons() {
        return couponRepository.currentlyValid(LocalDateTime.now(), PageRequest.of(0, 3)).stream()
                .map(PublicCouponResponse::from).toList();
    }

    @PostMapping("/contact")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse contact(@Valid @RequestBody ContactRequest request) {
        return contactService.submit(request);
    }

    /** Serves an uploaded image. Names are random and validated, so files cannot be guessed or traversed. */
    @GetMapping("/images/{name}")
    public ResponseEntity<Resource> image(@PathVariable String name) {
        Resource resource = fileStorageService.loadImage(name);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(FileStorageService.contentTypeFor(name)))
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30)).cachePublic())
                .body(resource);
    }
}
