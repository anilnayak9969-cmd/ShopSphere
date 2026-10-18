package com.example.ecommerce.dtos.account;

import com.example.ecommerce.entites.Address;

public record AddressResponse(
        Long addressId,
        String label,
        String fullName,
        String phone,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String pincode,
        boolean defaultAddress) {

    public static AddressResponse from(Address a) {
        return new AddressResponse(a.getAddressId(), a.getLabel(), a.getFullName(), a.getPhone(), a.getAddressLine1(),
                a.getAddressLine2(), a.getCity(), a.getState(), a.getPincode(), a.isDefaultAddress());
    }
}
