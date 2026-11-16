package com.example.ecommerce.services.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dtos.admin.CustomerCreateRequest;
import com.example.ecommerce.dtos.admin.CustomerDetailResponse;
import com.example.ecommerce.dtos.admin.CustomerResponse;
import com.example.ecommerce.dtos.admin.CustomerResponse.OrderStats;
import com.example.ecommerce.dtos.admin.CustomerSummaryResponse;
import com.example.ecommerce.dtos.admin.CustomerUpdateRequest;
import com.example.ecommerce.dtos.common.PageResponse;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.OrderStatus;
import com.example.ecommerce.enums.Role;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.OrderRepository;
import com.example.ecommerce.repositories.UserRepository;
import com.example.ecommerce.services.AddressService;
import com.example.ecommerce.services.CustomerAdminService;
import com.example.ecommerce.services.NotificationService;
import com.example.ecommerce.services.OrderService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomerAdminServiceImpl implements CustomerAdminService {

    /** Orders that do not count towards "total spent". */
    private static final EnumSet<OrderStatus> NOT_SPENT =
            EnumSet.of(OrderStatus.CANCELLED, OrderStatus.RETURNED, OrderStatus.REFUNDED);

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final AddressService addressService;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(String search, Boolean active, int page, int size) {
        String term = search == null || search.isBlank() ? null : search.trim();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by("createdAt").descending());
        Page<User> customers = userRepository.searchCustomers(active, term, pageable);
        Map<Long, OrderStats> stats = stats(customers.getContent().stream().map(User::getId).toList());
        return PageResponse.of(customers, u -> CustomerResponse.from(u, stats.getOrDefault(u.getId(), OrderStats.EMPTY)));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerSummaryResponse summary() {
        long active = userRepository.countByRoleAndActive(Role.CUSTOMER, true);
        long inactive = userRepository.countByRoleAndActive(Role.CUSTOMER, false);
        return new CustomerSummaryResponse(active + inactive, active, inactive,
                userRepository.countByRoleAndCreatedAtAfter(Role.CUSTOMER, LocalDateTime.now().minusDays(30)),
                orderRepository.countCustomersWithOrders());
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerDetailResponse get(Long customerId) {
        User customer = findCustomer(customerId);
        return new CustomerDetailResponse(toResponse(customer), addressService.list(customerId),
                orderService.customerOrders(customerId, 0, 10).content());
    }

    @Override
    @Transactional
    public CustomerResponse create(Long adminId, CustomerCreateRequest request) {
        String email = normaliseEmail(request.email());
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw ApiException.conflict("EMAIL_EXISTS", "An account with this email already exists.");
        }
        User customer = new User();
        customer.setFullName(cleanName(request.fullName()));
        customer.setEmail(email);
        customer.setPhone(request.phone());
        customer.setPassword(passwordEncoder.encode(request.password()));
        customer.setRole(Role.CUSTOMER);
        customer.setEmailVerified(true); // added by a trusted admin, so no email code is needed
        customer.setActive(true);
        User saved = userRepository.saveAndFlush(customer);
        notificationService.notify(saved, "Welcome to ShopSphere",
                "Your account was created by the ShopSphere team. Please change your password from My account.",
                "/account");
        return toResponse(saved);
    }

    @Override
    @Transactional
    public CustomerResponse update(Long customerId, CustomerUpdateRequest request) {
        User customer = findCustomer(customerId);
        String email = normaliseEmail(request.email());
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, customerId)) {
            throw ApiException.conflict("EMAIL_EXISTS", "Another account already uses this email.");
        }
        customer.setFullName(cleanName(request.fullName()));
        customer.setEmail(email);
        customer.setPhone(request.phone());
        boolean statusChanged = customer.isActive() != request.active();
        customer.setActive(request.active());
        User saved = userRepository.saveAndFlush(customer);
        if (!statusChanged || saved.isActive()) {
            notificationService.notify(saved, "Account details updated",
                    "The ShopSphere team updated your contact details. Contact support if this was not expected.",
                    "/account");
        }
        return toResponse(saved);
    }

    @Override
    @Transactional
    public CustomerResponse setActive(Long customerId, boolean active) {
        User customer = findCustomer(customerId);
        customer.setActive(active);
        return toResponse(userRepository.saveAndFlush(customer));
    }

    // --------------------------------------------------------------- helpers

    private User findCustomer(Long id) {
        return userRepository.findById(id)
                .filter(u -> u.getRole() == Role.CUSTOMER)
                .orElseThrow(() -> ApiException.notFound("Customer " + id + " was not found."));
    }

    private CustomerResponse toResponse(User u) {
        return CustomerResponse.from(u, stats(List.of(u.getId())).getOrDefault(u.getId(), OrderStats.EMPTY));
    }

    private Map<Long, OrderStats> stats(List<Long> customerIds) {
        if (customerIds.isEmpty()) {
            return Map.of();
        }
        return orderRepository.customerOrderStats(customerIds, NOT_SPENT).stream()
                .collect(Collectors.toMap(r -> (Long) r[0],
                        r -> new OrderStats(((Number) r[1]).longValue(), toMoney(r[2]), (LocalDateTime) r[3])));
    }

    private static BigDecimal toMoney(Object value) {
        return value instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(value));
    }

    private static String normaliseEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Trims and collapses repeated spaces, e.g. "  Rahul   Mehta " -> "Rahul Mehta". */
    private static String cleanName(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }
}
