package com.example.ecommerce.services;

import java.util.List;

import com.example.ecommerce.dtos.account.AddressRequest;
import com.example.ecommerce.dtos.account.AddressResponse;

public interface AddressService {

    List<AddressResponse> list(Long userId);

    AddressResponse create(Long userId, AddressRequest request);

    AddressResponse update(Long userId, Long addressId, AddressRequest request);

    void delete(Long userId, Long addressId);

    AddressResponse makeDefault(Long userId, Long addressId);
}
