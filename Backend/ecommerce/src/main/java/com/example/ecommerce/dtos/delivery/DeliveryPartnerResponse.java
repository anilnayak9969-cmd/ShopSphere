package com.example.ecommerce.dtos.delivery;

import java.time.LocalDateTime;

import com.example.ecommerce.entites.DeliveryPartnerProfile;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.SellerStatus;
import com.example.ecommerce.enums.VehicleType;

/** A delivery partner as the partner (own profile) or an admin (review list) sees them. */
public record DeliveryPartnerResponse(
        Long userId,
        String fullName,
        String email,
        String phone,
        SellerStatus status,
        boolean active,
        boolean emailVerified,
        VehicleType vehicleType,
        String vehicleNumber,
        String serviceCity,
        String servicePincode,
        LocalDateTime appliedAt) {

    public static DeliveryPartnerResponse from(DeliveryPartnerProfile p) {
        User u = p.getUser();
        return new DeliveryPartnerResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getSellerStatus(),
                u.isActive(), u.isEmailVerified(), p.getVehicleType(), p.getVehicleNumber(), p.getServiceCity(),
                p.getServicePincode(), u.getCreatedAt());
    }
}
