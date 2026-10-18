package com.example.ecommerce.services.impl;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dtos.account.ChangePasswordRequest;
import com.example.ecommerce.dtos.account.UpdateProfileRequest;
import com.example.ecommerce.dtos.auth.UserResponse;
import com.example.ecommerce.dtos.common.MessageResponse;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.Role;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.UserRepository;
import com.example.ecommerce.services.AccountService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = find(userId);
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone());
        if (user.getRole() == Role.SELLER) {
            if (request.storeName() == null || request.storeName().isBlank()) {
                throw ApiException.badRequest("STORE_NAME_REQUIRED", "Store name is required for sellers.");
            }
            user.setStoreName(request.storeName().trim());
        }
        return UserResponse.from(userRepository.saveAndFlush(user));
    }

    @Override
    @Transactional
    public MessageResponse changePassword(Long userId, ChangePasswordRequest request) {
        User user = find(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw ApiException.badRequest("WRONG_PASSWORD", "Your current password is incorrect.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw ApiException.badRequest("SAME_PASSWORD", "Choose a password different from your current one.");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        return new MessageResponse("Your password has been changed.");
    }

    private User find(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found."));
    }
}
