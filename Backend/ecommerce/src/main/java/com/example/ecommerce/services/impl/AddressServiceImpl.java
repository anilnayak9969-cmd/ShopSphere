package com.example.ecommerce.services.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dtos.account.AddressRequest;
import com.example.ecommerce.dtos.account.AddressResponse;
import com.example.ecommerce.entites.Address;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.AddressRepository;
import com.example.ecommerce.repositories.UserRepository;
import com.example.ecommerce.services.AddressService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private static final int MAX_ADDRESSES = 10;

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> list(Long userId) {
        return addressRepository.findByUserIdOrderByDefaultAddressDescUpdatedAtDesc(userId).stream()
                .map(AddressResponse::from).toList();
    }

    @Override
    @Transactional
    public AddressResponse create(Long userId, AddressRequest request) {
        long existing = addressRepository.countByUserId(userId);
        if (existing >= MAX_ADDRESSES) {
            throw ApiException.badRequest("ADDRESS_LIMIT", "You can save up to " + MAX_ADDRESSES + " addresses.");
        }
        // The first address is always the default.
        boolean makeDefault = request.isDefault() || existing == 0;
        if (makeDefault) {
            addressRepository.clearDefault(userId);
        }
        Address address = new Address();
        address.setUser(userRepository.getReferenceById(userId));
        apply(address, request);
        address.setDefaultAddress(makeDefault);
        return AddressResponse.from(addressRepository.saveAndFlush(address));
    }

    @Override
    @Transactional
    public AddressResponse update(Long userId, Long addressId, AddressRequest request) {
        if (request.isDefault()) {
            addressRepository.clearDefault(userId);
        }
        Address address = findOwned(userId, addressId);
        apply(address, request);
        if (request.isDefault()) {
            address.setDefaultAddress(true);
        }
        return AddressResponse.from(addressRepository.saveAndFlush(address));
    }

    @Override
    @Transactional
    public void delete(Long userId, Long addressId) {
        Address address = findOwned(userId, addressId);
        boolean wasDefault = address.isDefaultAddress();
        addressRepository.delete(address);
        addressRepository.flush();
        if (wasDefault) {
            // Promote the most recently used remaining address so there is always a default.
            addressRepository.findByUserIdOrderByDefaultAddressDescUpdatedAtDesc(userId).stream().findFirst()
                    .ifPresent(next -> next.setDefaultAddress(true));
        }
    }

    @Override
    @Transactional
    public AddressResponse makeDefault(Long userId, Long addressId) {
        addressRepository.clearDefault(userId);
        Address address = findOwned(userId, addressId);
        address.setDefaultAddress(true);
        return AddressResponse.from(addressRepository.saveAndFlush(address));
    }

    /** Looking up by id AND user id means nobody can read or change another customer's address. */
    private Address findOwned(Long userId, Long addressId) {
        return addressRepository.findByAddressIdAndUserId(addressId, userId)
                .orElseThrow(() -> ApiException.notFound("Address not found."));
    }

    private static void apply(Address address, AddressRequest r) {
        address.setLabel(r.label() == null || r.label().isBlank() ? null : r.label().trim());
        address.setFullName(r.fullName().trim());
        address.setPhone(r.phone());
        address.setAddressLine1(r.addressLine1().trim());
        address.setAddressLine2(r.addressLine2() == null || r.addressLine2().isBlank() ? null : r.addressLine2().trim());
        address.setCity(r.city().trim());
        address.setState(r.state().trim());
        address.setPincode(r.pincode());
    }
}
