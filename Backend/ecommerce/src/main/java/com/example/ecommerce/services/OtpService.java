package com.example.ecommerce.services;

import com.example.ecommerce.enums.OtpPurpose;

public interface OtpService {

    /** Generates a new 6-digit code, stores its hash and emails it. */
    void sendOtp(String email, String name, OtpPurpose purpose);

    /** Throws an ApiException if the code is wrong, expired or used too many times. */
    void verifyOtp(String email, OtpPurpose purpose, String code);
}
