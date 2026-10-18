package com.example.ecommerce.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ecommerce.dtos.account.ChangePasswordRequest;
import com.example.ecommerce.dtos.account.UpdateProfileRequest;
import com.example.ecommerce.dtos.auth.UserResponse;
import com.example.ecommerce.dtos.common.MessageResponse;
import com.example.ecommerce.security.UserPrincipal;
import com.example.ecommerce.services.AccountService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Any signed-in user (customer, seller or admin) can manage their own profile here. */
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PutMapping("/profile")
    public UserResponse updateProfile(@AuthenticationPrincipal UserPrincipal user,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        return accountService.updateProfile(user.getId(), request);
    }

    @PostMapping("/change-password")
    public MessageResponse changePassword(@AuthenticationPrincipal UserPrincipal user,
                                          @Valid @RequestBody ChangePasswordRequest request) {
        return accountService.changePassword(user.getId(), request);
    }
}
