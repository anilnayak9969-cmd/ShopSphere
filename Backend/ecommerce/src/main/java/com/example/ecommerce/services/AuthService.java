package com.example.ecommerce.services;

import com.example.ecommerce.dtos.auth.AuthResponse;
import com.example.ecommerce.dtos.auth.ForgotPasswordRequest;
import com.example.ecommerce.dtos.auth.LoginRequest;
import com.example.ecommerce.dtos.auth.RegisterRequest;
import com.example.ecommerce.dtos.auth.ResendOtpRequest;
import com.example.ecommerce.dtos.auth.ResetPasswordRequest;
import com.example.ecommerce.dtos.auth.UserResponse;
import com.example.ecommerce.dtos.auth.VerifyOtpRequest;
import com.example.ecommerce.dtos.common.MessageResponse;

public interface AuthService {

    MessageResponse register(RegisterRequest request);

    AuthResponse verifyRegistration(VerifyOtpRequest request);

    MessageResponse resendOtp(ResendOtpRequest request);

    AuthResponse login(LoginRequest request);

    MessageResponse forgotPassword(ForgotPasswordRequest request);

    MessageResponse resetPassword(ResetPasswordRequest request);

    UserResponse currentUser(Long userId);
}
