package com.example.ecommerce.services;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.dtos.delivery.DeliveryPartnerProfileRequest;
import com.example.ecommerce.dtos.delivery.DeliveryPartnerResponse;
import com.example.ecommerce.entites.DeliveryPartnerProfile;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.SellerStatus;
import com.example.ecommerce.enums.VehicleType;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.DeliveryPartnerProfileRepository;

import lombok.RequiredArgsConstructor;

/**
 * Delivery partners (phase 1): registration, profile and admin review. Assigning deliveries to
 * partners is the next phase; until then sellers update shipments themselves.
 */
@Service
@RequiredArgsConstructor
public class DeliveryPartnerService {

    private final DeliveryPartnerProfileRepository profileRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public DeliveryPartnerResponse get(Long userId) {
        return DeliveryPartnerResponse.from(find(userId));
    }

    @Transactional
    public DeliveryPartnerResponse update(Long userId, DeliveryPartnerProfileRequest r) {
        DeliveryPartnerProfile profile = find(userId);
        String number = null;
        if (r.vehicleType().needsRegistrationNumber()) {
            number = VehicleType.normaliseNumber(r.vehicleNumber());
            if (number == null) {
                throw ApiException.badRequest("VEHICLE_NUMBER_INVALID", "Enter a valid vehicle registration number, e.g. MH12AB1234.");
            }
        }
        profile.setVehicleType(r.vehicleType());
        profile.setVehicleNumber(number);
        profile.setServiceCity(r.serviceCity().trim());
        profile.setServicePincode(r.servicePincode().trim());
        return DeliveryPartnerResponse.from(profileRepository.saveAndFlush(profile));
    }

    @Transactional(readOnly = true)
    public PageResponse<DeliveryPartnerResponse> list(SellerStatus status, String search, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by("createdAt").descending());
        String term = search == null || search.isBlank() ? null : search.trim();
        return PageResponse.of(profileRepository.search(status, term, pageable), DeliveryPartnerResponse::from);
    }

    @Transactional
    public DeliveryPartnerResponse updateStatus(Long userId, SellerStatus status) {
        DeliveryPartnerProfile profile = find(userId);
        User partner = profile.getUser();
        partner.setSellerStatus(status);
        String message = switch (status) {
            case APPROVED -> "Your delivery partner application has been approved. Welcome aboard!";
            case REJECTED -> "Your delivery partner application was not approved.";
            case SUSPENDED -> "Your delivery partner account has been suspended.";
            case PENDING -> "Your delivery partner application is under review.";
        };
        notificationService.notify(partner, "Application " + status.name().toLowerCase(), message, "/delivery");
        return DeliveryPartnerResponse.from(profile);
    }

    private DeliveryPartnerProfile find(Long userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Delivery partner " + userId + " was not found."));
    }
}
