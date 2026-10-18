package com.example.ecommerce.dtos.auth;

public record AuthResponse(String token, String tokenType, long expiresInMs, UserResponse user) {
}
