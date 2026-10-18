package com.example.ecommerce.services;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dtos.common.NotificationResponse;
import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.entites.Notification;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.Role;
import com.example.ecommerce.repositories.NotificationRepository;
import com.example.ecommerce.repositories.UserRepository;

import lombok.RequiredArgsConstructor;

/** In-app notifications (the bell icon). Each user only ever reads their own. */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final LiveEventService liveEventService;

    @Transactional
    public void notify(User user, String title, String message, String link) {
        Notification n = new Notification();
        n.setUser(user);
        n.setTitle(truncate(title, 120));
        n.setMessage(truncate(message, 500));
        n.setLink(link);
        notificationRepository.save(n);
        // Push to the user's open tabs right away (after commit), so pages can refresh themselves.
        liveEventService.toUser(user.getId(), "notification", NotificationResponse.from(n));
    }

    @Transactional
    public void notifyAdmins(String title, String message, String link) {
        userRepository.findByRoleAndActiveTrue(Role.ADMIN).forEach(admin -> notify(admin, title, message, link));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, int page, int size) {
        return PageResponse.of(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50))), NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        notificationRepository.markRead(notificationId, userId);
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
