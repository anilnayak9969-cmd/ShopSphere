package com.example.ecommerce.exception;

import java.time.LocalDateTime;
import java.util.Map;

/** The JSON body of every error response. fieldErrors is filled for form validation failures. */
public record ApiError(
        LocalDateTime timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors) {
}
