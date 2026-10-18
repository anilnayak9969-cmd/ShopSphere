package com.example.ecommerce.dtos.common;

import java.time.LocalDateTime;

import com.example.ecommerce.entites.ContactMessage;

public record ContactMessageResponse(Long messageId, String name, String email, String subject, String message,
                                     boolean resolved, LocalDateTime createdAt) {

    public static ContactMessageResponse from(ContactMessage m) {
        return new ContactMessageResponse(m.getMessageId(), m.getName(), m.getEmail(), m.getSubject(), m.getMessage(),
                m.isResolved(), m.getCreatedAt());
    }
}
