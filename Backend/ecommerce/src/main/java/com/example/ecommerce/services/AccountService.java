package com.example.ecommerce.services;

import com.example.ecommerce.dtos.account.ChangePasswordRequest;
import com.example.ecommerce.dtos.account.UpdateProfileRequest;
import com.example.ecommerce.dtos.auth.UserResponse;
import com.example.ecommerce.dtos.common.MessageResponse;

public interface AccountService {

    UserResponse updateProfile(Long userId, UpdateProfileRequest request);

    MessageResponse changePassword(Long userId, ChangePasswordRequest request);
}
