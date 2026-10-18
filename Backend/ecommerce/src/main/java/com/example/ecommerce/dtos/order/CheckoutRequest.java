package com.example.ecommerce.dtos.order;

import com.example.ecommerce.dtos.common.ValidationPatterns;
import com.example.ecommerce.enums.PaymentMethod;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The delivery address, payment method and optional coupon. Prices are never accepted from the browser. */
public record CheckoutRequest(
        @NotBlank(message = "Recipient name is required") @Size(max = 100) String shippingName,
        @NotBlank(message = "Mobile number is required")
        @Pattern(regexp = ValidationPatterns.PHONE, message = ValidationPatterns.PHONE_MESSAGE) String shippingPhone,
        @NotBlank(message = "Address is required") @Size(max = 200) String addressLine1,
        @Size(max = 200) String addressLine2,
        @NotBlank(message = "City is required") @Size(max = 80) String city,
        @NotBlank(message = "State is required") @Size(max = 80) String state,
        @NotBlank(message = "PIN code is required")
        @Pattern(regexp = ValidationPatterns.PINCODE, message = "Enter a valid 6-digit PIN code") String pincode,
        /* Optional. Re-validated on the server; the browser never sends the discount amount. */
        @Size(max = 30) String couponCode,
        /* RAZORPAY (default) or COD. COD is only allowed when enabled in platform settings. */
        PaymentMethod paymentMethod) {

    public PaymentMethod method() {
        return paymentMethod == null ? PaymentMethod.RAZORPAY : paymentMethod;
    }
}
