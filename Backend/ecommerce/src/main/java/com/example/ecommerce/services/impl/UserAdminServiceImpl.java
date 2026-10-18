package com.example.ecommerce.services.impl;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dtos.admin.CreateAdminRequest;
import com.example.ecommerce.dtos.auth.UserResponse;
import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.Role;
import com.example.ecommerce.enums.SellerStatus;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.UserRepository;
import com.example.ecommerce.services.NotificationService;
import com.example.ecommerce.services.UserAdminService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserAdminServiceImpl implements UserAdminService {

    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(Role role, SellerStatus sellerStatus, String search, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by("createdAt").descending());
        String term = search == null || search.isBlank() ? null : search.trim();
        return PageResponse.of(userRepository.search(role, sellerStatus, term, pageable), UserResponse::from);
    }

    @Override
    @Transactional
    public UserResponse updateSellerStatus(Long sellerId, SellerStatus status) {
        User seller = userRepository.findById(sellerId)
                .filter(u -> u.getRole() == Role.SELLER)
                .orElseThrow(() -> ApiException.notFound("Seller " + sellerId + " was not found."));
        seller.setSellerStatus(status);
        String message = switch (status) {
            case APPROVED -> "Your store has been approved. You can now list products.";
            case REJECTED -> "Your seller application was not approved.";
            case SUSPENDED -> "Your store has been suspended; your products are hidden from customers.";
            case PENDING -> "Your store is under review.";
        };
        notificationService.notify(seller, "Store " + status.name().toLowerCase(), message, "/seller");
        return UserResponse.from(userRepository.saveAndFlush(seller));
    }

    @Override
    @Transactional
    public UserResponse setUserActive(Long adminId, Long userId, boolean active) {
        if (adminId.equals(userId)) {
            throw ApiException.badRequest("SELF_UPDATE", "You cannot suspend your own account.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User " + userId + " was not found."));
        if (user.getRole() == Role.ADMIN) {
            throw ApiException.forbidden("ADMIN_PROTECTED", "Administrator accounts cannot be suspended here.");
        }
        user.setActive(active);
        return UserResponse.from(userRepository.saveAndFlush(user));
    }

    @Override
    @Transactional
    public UserResponse createAdmin(Long creatorId, CreateAdminRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw ApiException.conflict("EMAIL_EXISTS", "An account with this email already exists.");
        }
        User admin = new User();
        admin.setFullName(request.fullName().trim());
        admin.setEmail(email);
        admin.setPhone(request.phone());
        admin.setPassword(passwordEncoder.encode(request.password()));
        admin.setRole(Role.ADMIN);
        admin.setEmailVerified(true); // created by a trusted admin, who shares the first password with them
        admin.setActive(true);
        User saved = userRepository.saveAndFlush(admin);
        User creator = userRepository.findById(creatorId).orElse(null);
        notificationService.notify(saved, "Welcome to the admin team",
                "Your admin account was created" + (creator == null ? "" : " by " + creator.getFullName())
                        + ". Please change your password from My account.", "/account");
        return UserResponse.from(saved);
    }
}
