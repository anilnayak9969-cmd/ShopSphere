package com.example.ecommerce.dtos.common;

/** Regular expressions shared by request DTOs. */
public final class ValidationPatterns {

    private ValidationPatterns() {
    }

    /** 8+ characters with at least one uppercase letter, one lowercase letter and one digit. */
    public static final String PASSWORD = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,64}$";
    public static final String PASSWORD_MESSAGE =
            "Password must be 8-64 characters and include an uppercase letter, a lowercase letter and a number";

    /** Indian mobile number: 10 digits starting with 6-9. */
    public static final String PHONE = "^[6-9]\\d{9}$";
    public static final String PHONE_MESSAGE = "Enter a valid 10-digit mobile number";

    /** A person's name: letters (any language), spaces, dot, apostrophe and hyphen — no digits or symbols. */
    public static final String PERSON_NAME = "^\\p{L}[\\p{L}\\p{M} .'-]*$"; // \p{M}: vowel signs in Hindi and other Indian scripts
    public static final String PERSON_NAME_MESSAGE = "Use letters, spaces and . ' - only";

    public static final String PINCODE = "^[1-9]\\d{5}$";
    public static final String OTP = "^\\d{6}$";

    /** Empty, an http(s) URL, or an image uploaded to this server (/api/images/&lt;uuid&gt;.jpg). */
    public static final String HTTP_URL = "^$|^(https?://\\S+|/api/images/[A-Za-z0-9._-]+)$";
    public static final String IMAGE_URL_MESSAGE = "Upload an image or enter a link starting with http:// or https://";
}
