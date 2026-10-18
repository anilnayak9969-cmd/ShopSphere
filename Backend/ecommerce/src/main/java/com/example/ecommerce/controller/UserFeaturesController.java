package com.example.ecommerce.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.ecommerce.dtos.common.NotificationResponse;
import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.common.UploadResponse;
import com.example.ecommerce.security.UserPrincipal;
import com.example.ecommerce.services.FileStorageService;
import com.example.ecommerce.services.NotificationService;

import lombok.RequiredArgsConstructor;

/** Features shared by every signed-in role: notifications, and image upload for sellers/admins. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserFeaturesController {

    private final NotificationService notificationService;
    private final FileStorageService fileStorageService;

    @GetMapping("/notifications")
    public PageResponse<NotificationResponse> notifications(@AuthenticationPrincipal UserPrincipal user,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "10") int size) {
        return notificationService.list(user.getId(), page, size);
    }

    @GetMapping("/notifications/unread-count")
    public long unreadCount(@AuthenticationPrincipal UserPrincipal user) {
        return notificationService.unreadCount(user.getId());
    }

    @PatchMapping("/notifications/{id}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal UserPrincipal user, @PathVariable Long id) {
        notificationService.markRead(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/notifications/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal UserPrincipal user) {
        notificationService.markAllRead(user.getId());
        return ResponseEntity.noContent().build();
    }

    /** multipart/form-data with a "file" part. JPG, PNG or WEBP up to 5 MB. */
    @PostMapping("/uploads/images")
    @PreAuthorize("hasAnyRole('SELLER','ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponse uploadImage(@RequestPart("file") MultipartFile file) {
        return fileStorageService.storeImage(file);
    }
}
