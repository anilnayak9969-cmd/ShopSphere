package com.example.ecommerce.services;

import com.example.ecommerce.entites.CustomerOrder;
import com.example.ecommerce.enums.OtpPurpose;

public interface EmailService {

    /** Sends a one-time code. Throws if the email cannot be sent, so the caller can report it. */
    void sendOtpEmail(String to, String name, String otp, OtpPurpose purpose, int expiryMinutes);

    /** Best effort: a failure is logged but never breaks the order that was already paid for. */
    void sendOrderConfirmation(CustomerOrder order);
}
