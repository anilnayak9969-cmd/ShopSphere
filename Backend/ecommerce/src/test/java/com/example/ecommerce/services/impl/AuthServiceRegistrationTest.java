package com.example.ecommerce.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.ecommerce.dtos.auth.RegisterRequest;
import com.example.ecommerce.entites.DeliveryPartnerProfile;
import com.example.ecommerce.entites.SellerProfile;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.BusinessType;
import com.example.ecommerce.enums.OtpPurpose;
import com.example.ecommerce.enums.Role;
import com.example.ecommerce.enums.SellerStatus;
import com.example.ecommerce.enums.VehicleType;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.DeliveryPartnerProfileRepository;
import com.example.ecommerce.repositories.SellerProfileRepository;
import com.example.ecommerce.repositories.UserRepository;
import com.example.ecommerce.security.LoginAttemptService;
import com.example.ecommerce.security.jwt.JwtUtils;
import com.example.ecommerce.services.NotificationService;
import com.example.ecommerce.services.OtpService;

/** Registration rules for each self-service role: customer, seller and delivery partner. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceRegistrationTest {

    @Mock private UserRepository userRepository;
    @Mock private OtpService otpService;
    @Mock private JwtUtils jwtUtils;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private SellerProfileRepository sellerProfileRepository;
    @Mock private DeliveryPartnerProfileRepository deliveryPartnerProfileRepository;
    @Mock private NotificationService notificationService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, new BCryptPasswordEncoder(4), otpService, jwtUtils,
                loginAttemptService, sellerProfileRepository, deliveryPartnerProfileRepository, notificationService);
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(7L);
            return u;
        });
        when(sellerProfileRepository.findByUserId(any())).thenReturn(Optional.empty());
        when(deliveryPartnerProfileRepository.findByUserId(any())).thenReturn(Optional.empty());
    }

    private static RegisterRequest request(Role role, Boolean terms, String storeName, BusinessType type,
                                           String pickupAddress, VehicleType vehicle, String vehicleNumber,
                                           String serviceCity) {
        return new RegisterRequest("Asha Rao", "asha@example.com", "9876543210", "Secret@123", role, terms,
                storeName, type, "", pickupAddress, "Pune", "Maharashtra", "411001",
                vehicle, vehicleNumber, serviceCity, "411001", null);
    }

    private static RegisterRequest adminRequest(String code) {
        return new RegisterRequest("Neha Admin", "neha@example.com", "9876543211", "Secret@123", Role.ADMIN, true,
                null, null, "", null, null, null, null, null, null, null, null, code);
    }

    @Test
    void customerNeedsOnlyTheBasicsAndGetsNoApplicationStatus() {
        authService.register(request(Role.CUSTOMER, true, null, null, null, null, null, null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(saved.getValue().getSellerStatus()).isNull();
        verify(otpService).sendOtp(eq("asha@example.com"), anyString(), eq(OtpPurpose.REGISTRATION));
        verify(sellerProfileRepository, never()).save(any());
    }

    @Test
    void adminSignUpIsOffWhenNoAccessCodeIsConfigured() {
        assertThatThrownBy(() -> authService.register(adminRequest("anything")))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("ADMIN_REGISTRATION_DISABLED");
        verify(userRepository, never()).save(any());
    }

    @Test
    void adminSignUpNeedsTheRightAccessCode() {
        ReflectionTestUtils.setField(authService, "adminRegistrationCode", "Team-Code-2026");
        assertThatThrownBy(() -> authService.register(adminRequest("wrong-code")))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("ADMIN_CODE_INVALID");
        assertThatThrownBy(() -> authService.register(adminRequest(null)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("ADMIN_CODE_INVALID");
        verify(loginAttemptService, org.mockito.Mockito.times(2)).recordFailure("admin-registration-code");
        verify(userRepository, never()).save(any());
    }

    @Test
    void adminWithTheRightAccessCodeIsCreatedAsAdmin() {
        ReflectionTestUtils.setField(authService, "adminRegistrationCode", "Team-Code-2026");
        authService.register(adminRequest(" Team-Code-2026 "));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().getSellerStatus()).isNull();
        verify(otpService).sendOtp(eq("neha@example.com"), anyString(), eq(OtpPurpose.REGISTRATION));
    }

    @Test
    void termsMustBeAccepted() {
        assertThatThrownBy(() -> authService.register(request(Role.CUSTOMER, false, null, null, null, null, null, null)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("TERMS_REQUIRED");
    }

    @Test
    void sellerNeedsBusinessTypeAndPickupAddress() {
        assertThatThrownBy(() -> authService.register(request(Role.SELLER, true, "Asha Crafts", null, "12 MG Road", null, null, null)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("BUSINESS_TYPE_REQUIRED");
        assertThatThrownBy(() -> authService.register(request(Role.SELLER, true, "Asha Crafts", BusinessType.INDIVIDUAL, " ", null, null, null)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("PICKUP_ADDRESS_REQUIRED");
    }

    @Test
    void sellerRegistrationCreatesPendingStoreWithProfile() {
        authService.register(request(Role.SELLER, true, "Asha Crafts", BusinessType.PROPRIETORSHIP, "12 MG Road", null, null, null));

        ArgumentCaptor<SellerProfile> profile = ArgumentCaptor.forClass(SellerProfile.class);
        verify(sellerProfileRepository).save(profile.capture());
        assertThat(profile.getValue().getBusinessType()).isEqualTo(BusinessType.PROPRIETORSHIP);
        assertThat(profile.getValue().getCity()).isEqualTo("Pune");
        assertThat(profile.getValue().getUser().getSellerStatus()).isEqualTo(SellerStatus.PENDING);
        assertThat(profile.getValue().getUser().getStoreName()).isEqualTo("Asha Crafts");
    }

    @Test
    void deliveryPartnerOnMotorcycleNeedsAValidRegistrationNumber() {
        assertThatThrownBy(() -> authService.register(request(Role.DELIVERY_PARTNER, true, null, null, null, VehicleType.MOTORCYCLE, "12345", "Pune")))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("VEHICLE_NUMBER_INVALID");

        authService.register(request(Role.DELIVERY_PARTNER, true, null, null, null, VehicleType.MOTORCYCLE, "mh 12-ab 1234", "Pune"));
        ArgumentCaptor<DeliveryPartnerProfile> profile = ArgumentCaptor.forClass(DeliveryPartnerProfile.class);
        verify(deliveryPartnerProfileRepository).save(profile.capture());
        assertThat(profile.getValue().getVehicleNumber()).isEqualTo("MH12AB1234");
        assertThat(profile.getValue().getUser().getSellerStatus()).isEqualTo(SellerStatus.PENDING);
    }

    @Test
    void deliveryPartnerOnBicycleNeedsNoVehicleNumberButNeedsAServiceArea() {
        assertThatThrownBy(() -> authService.register(request(Role.DELIVERY_PARTNER, true, null, null, null, VehicleType.BICYCLE, null, " ")))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("SERVICE_AREA_REQUIRED");

        authService.register(request(Role.DELIVERY_PARTNER, true, null, null, null, VehicleType.BICYCLE, null, "Pune"));
        ArgumentCaptor<DeliveryPartnerProfile> profile = ArgumentCaptor.forClass(DeliveryPartnerProfile.class);
        verify(deliveryPartnerProfileRepository).save(profile.capture());
        assertThat(profile.getValue().getVehicleNumber()).isNull();
    }

    @Test
    void personNamePattern() {
        String rule = com.example.ecommerce.dtos.common.ValidationPatterns.PERSON_NAME;
        for (String ok : new String[]{"Anil Nayak", "A. R. Rahman", "D'Souza", "Mary-Jane", "अनिल नायक"}) {
            assertThat(ok.matches(rule)).as(ok).isTrue();
        }
        for (String bad : new String[]{"abc123", "Ravi@Kumar", " Neha", ".Anil", "<script>"}) {
            assertThat(bad.matches(rule)).as(bad).isFalse();
        }
    }

    @Test
    void vehicleNumberFormats() {
        assertThat(VehicleType.normaliseNumber("KA 01 HH 1234")).isEqualTo("KA01HH1234");
        assertThat(VehicleType.normaliseNumber("22 BH 1234 AA")).isEqualTo("22BH1234AA");
        assertThat(VehicleType.normaliseNumber("DL3C1234")).isEqualTo("DL3C1234");
        assertThat(VehicleType.normaliseNumber("NOT-A-NUMBER")).isNull();
    }
}
