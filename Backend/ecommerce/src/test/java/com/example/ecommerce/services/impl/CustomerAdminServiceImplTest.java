package com.example.ecommerce.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.ecommerce.dtos.admin.CustomerCreateRequest;
import com.example.ecommerce.dtos.admin.CustomerResponse;
import com.example.ecommerce.dtos.admin.CustomerUpdateRequest;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.Role;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.OrderRepository;
import com.example.ecommerce.repositories.UserRepository;
import com.example.ecommerce.services.AddressService;
import com.example.ecommerce.services.NotificationService;
import com.example.ecommerce.services.OrderService;

/** Customer Management module: add, update and deactivate rules. */
@ExtendWith(MockitoExtension.class)
class CustomerAdminServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderService orderService;
    @Mock private AddressService addressService;
    @Mock private NotificationService notificationService;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private CustomerAdminServiceImpl service;

    @BeforeEach
    void noOrdersByDefault() {
        lenient().when(orderRepository.customerOrderStats(anyCollection(), anyCollection())).thenReturn(List.of());
    }

    @Test
    void adminAddsAVerifiedActiveCustomerWithAHashedPassword() {
        when(userRepository.findByEmailIgnoreCase("rahul.mehta@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Welcome@123")).thenReturn("HASH");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(40L);
            return u;
        });

        CustomerResponse created = service.create(1L,
                new CustomerCreateRequest("  Rahul   Mehta ", " Rahul.Mehta@Example.com ", "9876543210", "Welcome@123"));

        assertThat(created.fullName()).isEqualTo("Rahul Mehta");
        assertThat(created.firstName()).isEqualTo("Rahul");
        assertThat(created.lastName()).isEqualTo("Mehta");
        assertThat(created.email()).isEqualTo("rahul.mehta@example.com");
        assertThat(created.active()).isTrue();
        assertThat(created.emailVerified()).isTrue();
        assertThat(created.orderCount()).isZero();
        verify(notificationService).notify(any(User.class), anyString(), anyString(), anyString());
    }

    @Test
    void duplicateEmailIsRefused() {
        when(userRepository.findByEmailIgnoreCase("priya@example.com")).thenReturn(Optional.of(customer(8L, true)));

        assertThatThrownBy(() -> service.create(1L,
                new CustomerCreateRequest("Priya Sharma", "priya@example.com", "9876543210", "Welcome@123")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateChangesContactDetailsAndStatus() {
        User priya = customer(8L, true);
        when(userRepository.findById(8L)).thenReturn(Optional.of(priya));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot("priya.s@example.com", 8L)).thenReturn(false);
        when(userRepository.saveAndFlush(priya)).thenReturn(priya);

        CustomerResponse updated = service.update(8L,
                new CustomerUpdateRequest("Priya Sharma Rao", "Priya.S@example.com", "9123456780", false));

        assertThat(updated.lastName()).isEqualTo("Sharma Rao");
        assertThat(updated.email()).isEqualTo("priya.s@example.com");
        assertThat(updated.phone()).isEqualTo("9123456780");
        assertThat(updated.active()).isFalse();
    }

    @Test
    void updateRefusesAnEmailUsedByAnotherAccount() {
        when(userRepository.findById(8L)).thenReturn(Optional.of(customer(8L, true)));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot("taken@example.com", 8L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(8L,
                new CustomerUpdateRequest("Priya Sharma", "taken@example.com", "9876543210", true)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already uses this email");
    }

    @Test
    void deactivateIsASoftDelete() {
        User priya = customer(8L, true);
        when(userRepository.findById(8L)).thenReturn(Optional.of(priya));
        when(userRepository.saveAndFlush(priya)).thenReturn(priya);

        CustomerResponse response = service.setActive(8L, false);

        assertThat(response.active()).isFalse();
        verify(userRepository, never()).delete(any(User.class));
        verify(userRepository, never()).deleteById(any());
    }

    @Test
    void onlyCustomerAccountsAreManagedHere() {
        User seller = customer(9L, true);
        seller.setRole(Role.SELLER);
        when(userRepository.findById(9L)).thenReturn(Optional.of(seller));

        assertThatThrownBy(() -> service.setActive(9L, false))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("was not found");
    }

    @Test
    void orderHistoryTotalsAreAttachedToTheCustomer() {
        User priya = customer(8L, true);
        when(userRepository.findById(8L)).thenReturn(Optional.of(priya));
        when(userRepository.saveAndFlush(priya)).thenReturn(priya);
        LocalDateTime last = LocalDateTime.of(2026, 11, 16, 19, 40);
        when(orderRepository.customerOrderStats(anyCollection(), anyCollection()))
                .thenReturn(List.<Object[]>of(new Object[]{8L, 6L, new BigDecimal("7609.24"), last}));

        CustomerResponse response = service.setActive(8L, true);

        assertThat(response.orderCount()).isEqualTo(6);
        assertThat(response.totalSpent()).isEqualByComparingTo("7609.24");
        assertThat(response.lastOrderAt()).isEqualTo(last);
    }

    private static User customer(Long id, boolean active) {
        User u = new User();
        u.setId(id);
        u.setFullName("Priya Sharma");
        u.setEmail("priya@example.com");
        u.setPhone("9000000010");
        u.setRole(Role.CUSTOMER);
        u.setEmailVerified(true);
        u.setActive(active);
        return u;
    }
}
