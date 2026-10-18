package com.example.ecommerce.dtos.common;

import java.time.LocalDateTime;

import com.example.ecommerce.entites.Notification;

public record NotificationResponse(Long notificationId, String title, String message, String link, boolean read,
                                   LocalDateTime createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getNotificationId(), n.getTitle(), n.getMessage(), n.getLink(), n.isRead(),
                n.getCreatedAt());
    }
}
