package com.example.ecommerce.dtos.account;

import java.time.LocalDateTime;

import com.example.ecommerce.entites.SellerProfile;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.BusinessType;
import com.example.ecommerce.enums.SellerStatus;

public record SellerProfileResponse(
        Long sellerId,
        String storeName,
        SellerStatus sellerStatus,
        String ownerName,
        String ownerEmail,
        BusinessType businessType,
        String description,
        String logoUrl,
        String supportEmail,
        String supportPhone,
        String gstin,
        String businessAddress,
        String city,
        String state,
        String pincode,
        LocalDateTime memberSince) {

    public static SellerProfileResponse from(User seller, SellerProfile p) {
        return new SellerProfileResponse(seller.getId(), seller.getStoreName(), seller.getSellerStatus(),
                seller.getFullName(), seller.getEmail(), p == null ? null : p.getBusinessType(),
                p == null ? null : p.getDescription(),
                p == null ? null : p.getLogoUrl(), p == null ? null : p.getSupportEmail(),
                p == null ? null : p.getSupportPhone(), p == null ? null : p.getGstin(),
                p == null ? null : p.getBusinessAddress(), p == null ? null : p.getCity(),
                p == null ? null : p.getState(), p == null ? null : p.getPincode(), seller.getCreatedAt());
    }
}
