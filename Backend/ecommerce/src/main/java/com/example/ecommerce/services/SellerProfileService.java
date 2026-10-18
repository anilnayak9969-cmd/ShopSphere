package com.example.ecommerce.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dtos.account.SellerProfileRequest;
import com.example.ecommerce.dtos.account.SellerProfileResponse;
import com.example.ecommerce.entites.SellerProfile;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.SellerProfileRepository;
import com.example.ecommerce.repositories.UserRepository;

import lombok.RequiredArgsConstructor;

/** The seller's Store Profile page. Sellers can only ever read and edit their own profile. */
@Service
@RequiredArgsConstructor
public class SellerProfileService {

    private final SellerProfileRepository profileRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public SellerProfileResponse get(Long sellerId) {
        User seller = findSeller(sellerId);
        return SellerProfileResponse.from(seller, profileRepository.findByUserId(sellerId).orElse(null));
    }

    @Transactional
    public SellerProfileResponse update(Long sellerId, SellerProfileRequest r) {
        User seller = findSeller(sellerId);
        seller.setStoreName(r.storeName().trim());
        SellerProfile profile = profileRepository.findByUserId(sellerId).orElseGet(() -> {
            SellerProfile p = new SellerProfile();
            p.setUser(seller);
            return p;
        });
        profile.setDescription(blank(r.description()));
        profile.setLogoUrl(blank(r.logoUrl()));
        profile.setSupportEmail(blank(r.supportEmail()));
        profile.setSupportPhone(blank(r.supportPhone()));
        profile.setGstin(r.gstin() == null || r.gstin().isBlank() ? null : r.gstin().trim().toUpperCase());
        profile.setBusinessAddress(blank(r.businessAddress()));
        profile.setCity(blank(r.city()));
        profile.setState(blank(r.state()));
        profile.setPincode(blank(r.pincode()));
        return SellerProfileResponse.from(seller, profileRepository.saveAndFlush(profile));
    }

    private User findSeller(Long sellerId) {
        return userRepository.findById(sellerId).orElseThrow(() -> ApiException.notFound("Seller not found."));
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
