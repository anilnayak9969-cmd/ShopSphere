package com.example.ecommerce.services.impl;

import java.util.Locale;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.dtos.auth.AuthResponse;
import com.example.ecommerce.dtos.auth.ForgotPasswordRequest;
import com.example.ecommerce.dtos.auth.LoginRequest;
import com.example.ecommerce.dtos.auth.RegisterRequest;
import com.example.ecommerce.dtos.auth.ResendOtpRequest;
import com.example.ecommerce.dtos.auth.ResetPasswordRequest;
import com.example.ecommerce.dtos.auth.UserResponse;
import com.example.ecommerce.dtos.auth.VerifyOtpRequest;
import com.example.ecommerce.dtos.common.MessageResponse;
import com.example.ecommerce.entites.DeliveryPartnerProfile;
import com.example.ecommerce.entites.SellerProfile;
import com.example.ecommerce.entites.User;
import com.example.ecommerce.enums.OtpPurpose;
import com.example.ecommerce.enums.Role;
import com.example.ecommerce.enums.SellerStatus;
import com.example.ecommerce.enums.VehicleType;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.DeliveryPartnerProfileRepository;
import com.example.ecommerce.repositories.SellerProfileRepository;
import com.example.ecommerce.repositories.UserRepository;
import com.example.ecommerce.security.LoginAttemptService;
import com.example.ecommerce.security.UserPrincipal;
import com.example.ecommerce.security.jwt.JwtUtils;
import com.example.ecommerce.services.AuthService;
import com.example.ecommerce.services.NotificationService;
import com.example.ecommerce.services.OtpService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final JwtUtils jwtUtils;
    private final LoginAttemptService loginAttemptService;
    private final SellerProfileRepository sellerProfileRepository;
    private final DeliveryPartnerProfileRepository deliveryPartnerProfileRepository;
    private final NotificationService notificationService;

    /** Checked when the email does not exist, so both cases take the same BCrypt time (hides which emails exist). */
    private volatile String dummyHash;

    /**
     * Secret code that lets someone register as ADMIN from the sign-up page. Empty = admin sign-up is off
     * (admins are then created from the secrets file or by another admin). Share it only with the team.
     */
    @Value("${app.admin.registration-code:}")
    private String adminRegistrationCode;

    /** All wrong admin codes share one counter, so the code cannot be guessed by switching emails. */
    private static final String ADMIN_CODE_LIMIT_KEY = "admin-registration-code";

    @Override
    @Transactional
    public MessageResponse register(RegisterRequest request) {
        if (request.role() == Role.ADMIN) {
            checkAdminCode(request.adminCode());
        } else if (request.role() != Role.CUSTOMER && request.role() != Role.SELLER
                && request.role() != Role.DELIVERY_PARTNER) {
            throw ApiException.badRequest("INVALID_ROLE", "You can register as a customer, a seller or a delivery partner.");
        }
        if (!request.termsAccepted()) {
            throw ApiException.badRequest("TERMS_REQUIRED", "Please accept the terms and privacy policy.");
        }
        boolean seller = request.role() == Role.SELLER;
        boolean partner = request.role() == Role.DELIVERY_PARTNER;
        if (seller) {
            validateSeller(request);
        }
        String vehicleNumber = partner ? validatePartner(request) : null;

        String email = normalize(request.email());
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user != null && user.isEmailVerified()) {
            throw ApiException.conflict("EMAIL_EXISTS", "An account with this email already exists. Please log in.");
        }
        if (user == null) {
            // An unverified account with this email is simply overwritten, so a typo during sign-up
            // never locks the address. Nobody can verify it without access to the inbox.
            user = new User();
            user.setEmail(email);
        }
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setEmailVerified(false);
        user.setActive(true);
        // Sellers and delivery partners are both reviewed by an admin before they can work.
        user.setSellerStatus(seller || partner ? SellerStatus.PENDING : null);
        user.setStoreName(seller ? request.storeName().trim() : null);
        userRepository.save(user);
        if (seller) {
            saveSellerProfile(user, request);
        }
        if (partner) {
            saveDeliveryProfile(user, request, vehicleNumber);
        }

        otpService.sendOtp(email, user.getFullName(), OtpPurpose.REGISTRATION);
        return new MessageResponse("We've sent a 6-digit verification code to " + email + ".");
    }

    @Override
    @Transactional
    public AuthResponse verifyRegistration(VerifyOtpRequest request) {
        String email = normalize(request.email());
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> ApiException.badRequest("OTP_INVALID", "No active code found. Please register again."));
        if (user.isEmailVerified()) {
            throw ApiException.conflict("ALREADY_VERIFIED", "This email is already verified. Please log in.");
        }
        otpService.verifyOtp(email, OtpPurpose.REGISTRATION, request.otp());
        user.setEmailVerified(true);
        if (user.getRole() == Role.SELLER) {
            notificationService.notifyAdmins("New seller application",
                    user.getStoreName() + " (" + user.getFullName() + ") is waiting for review.", "/admin/sellers?status=PENDING");
        } else if (user.getRole() == Role.DELIVERY_PARTNER) {
            notificationService.notifyAdmins("New delivery partner application",
                    user.getFullName() + " applied to deliver for ShopSphere.", "/admin/delivery-partners");
        } else if (user.getRole() == Role.ADMIN) {
            // Every admin hears about a new admin, so an unexpected one is noticed at once.
            notificationService.notifyAdmins("New admin registered",
                    user.getFullName() + " (" + user.getEmail() + ") registered as an admin with the access code.",
                    "/admin/users?role=ADMIN");
        }
        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public MessageResponse resendOtp(ResendOtpRequest request) {
        String email = normalize(request.email());
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        boolean eligible = user != null && user.isActive() && (request.purpose() == OtpPurpose.REGISTRATION
                ? !user.isEmailVerified()
                : user.isEmailVerified());
        if (eligible) {
            otpService.sendOtp(email, user.getFullName(), request.purpose());
        }
        // Same answer either way, so this endpoint cannot be used to discover registered emails.
        return new MessageResponse("If the email is eligible, a new code has been sent.");
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalize(request.email());
        loginAttemptService.checkNotBlocked(email);

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        boolean passwordMatches = passwordEncoder.matches(request.password(),
                user != null ? user.getPassword() : dummyHash());
        if (user == null || !passwordMatches) {
            loginAttemptService.recordFailure(email);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid email or password.");
        }
        if (!user.isActive()) {
            throw ApiException.forbidden("ACCOUNT_SUSPENDED", "This account has been suspended. Please contact support.");
        }
        if (!user.isEmailVerified()) {
            throw ApiException.forbidden("EMAIL_NOT_VERIFIED", "Please verify your email before logging in.");
        }
        loginAttemptService.reset(email);
        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String email = normalize(request.email());
        userRepository.findByEmailIgnoreCase(email)
                .filter(u -> u.isActive() && u.isEmailVerified())
                .ifPresent(u -> otpService.sendOtp(email, u.getFullName(), OtpPurpose.PASSWORD_RESET));
        return new MessageResponse("If an account exists for " + email + ", a reset code has been sent.");
    }

    @Override
    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        String email = normalize(request.email());
        User user = userRepository.findByEmailIgnoreCase(email)
                .filter(u -> u.isActive() && u.isEmailVerified())
                .orElseThrow(() -> ApiException.badRequest("OTP_INVALID", "No active code found. Please request a new one."));
        otpService.verifyOtp(email, OtpPurpose.PASSWORD_RESET, request.otp());
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        loginAttemptService.reset(email);
        return new MessageResponse("Your password has been reset. You can now log in.");
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse currentUser(Long userId) {
        return userRepository.findById(userId).map(UserResponse::from)
                .orElseThrow(() -> ApiException.notFound("User not found."));
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtUtils.generateToken(new UserPrincipal(user));
        return new AuthResponse(token, "Bearer", jwtUtils.getExpirationMs(), UserResponse.from(user));
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("timing-equaliser");
        }
        return dummyHash;
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
    // ------------------------------------------------------------------ role-specific registration

    private void checkAdminCode(String code) {
        if (adminRegistrationCode == null || adminRegistrationCode.isBlank()) {
            throw ApiException.forbidden("ADMIN_REGISTRATION_DISABLED",
                    "Admin registration is turned off on this server. Ask an existing admin to add you.");
        }
        loginAttemptService.checkNotBlocked(ADMIN_CODE_LIMIT_KEY);
        byte[] expected = adminRegistrationCode.trim().getBytes(StandardCharsets.UTF_8);
        byte[] given = (code == null ? "" : code.trim()).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, given)) { // constant-time comparison
            loginAttemptService.recordFailure(ADMIN_CODE_LIMIT_KEY);
            throw ApiException.forbidden("ADMIN_CODE_INVALID", "The admin access code is not correct.");
        }
    }

    private static void validateSeller(RegisterRequest r) {
        if (isBlank(r.storeName())) {
            throw ApiException.badRequest("STORE_NAME_REQUIRED", "Store name is required for seller registration.");
        }
        if (r.businessType() == null) {
            throw ApiException.badRequest("BUSINESS_TYPE_REQUIRED", "Choose your business type.");
        }
        if (isBlank(r.pickupAddress()) || isBlank(r.pickupCity()) || isBlank(r.pickupState()) || isBlank(r.pickupPincode())) {
            throw ApiException.badRequest("PICKUP_ADDRESS_REQUIRED",
                    "Enter the full pickup address (address, city, state and PIN code) where couriers collect your orders.");
        }
    }

    /** @return the normalised vehicle number (null for a bicycle) */
    private static String validatePartner(RegisterRequest r) {
        if (r.vehicleType() == null) {
            throw ApiException.badRequest("VEHICLE_TYPE_REQUIRED", "Choose the vehicle you will deliver with.");
        }
        if (isBlank(r.serviceCity()) || isBlank(r.servicePincode())) {
            throw ApiException.badRequest("SERVICE_AREA_REQUIRED", "Enter the city and PIN code where you want to deliver.");
        }
        if (!r.vehicleType().needsRegistrationNumber()) {
            return null;
        }
        String number = VehicleType.normaliseNumber(r.vehicleNumber());
        if (number == null) {
            throw ApiException.badRequest("VEHICLE_NUMBER_INVALID", "Enter a valid vehicle registration number, e.g. MH12AB1234.");
        }
        return number;
    }

    private void saveSellerProfile(User user, RegisterRequest r) {
        SellerProfile profile = sellerProfileRepository.findByUserId(user.getId()).orElse(null);
        if (profile == null) {
            profile = new SellerProfile();
            profile.setUser(user);
        }
        profile.setBusinessType(r.businessType());
        profile.setGstin(isBlank(r.gstin()) ? null : r.gstin().trim().toUpperCase(Locale.ROOT));
        profile.setBusinessAddress(r.pickupAddress().trim());
        profile.setCity(r.pickupCity().trim());
        profile.setState(r.pickupState().trim());
        profile.setPincode(r.pickupPincode().trim());
        profile.setSupportEmail(user.getEmail());
        profile.setSupportPhone(user.getPhone());
        sellerProfileRepository.save(profile);
    }

    private void saveDeliveryProfile(User user, RegisterRequest r, String vehicleNumber) {
        DeliveryPartnerProfile profile = deliveryPartnerProfileRepository.findByUserId(user.getId()).orElse(null);
        if (profile == null) {
            profile = new DeliveryPartnerProfile();
            profile.setUser(user);
        }
        profile.setVehicleType(r.vehicleType());
        profile.setVehicleNumber(vehicleNumber);
        profile.setServiceCity(r.serviceCity().trim());
        profile.setServicePincode(r.servicePincode().trim());
        deliveryPartnerProfileRepository.save(profile);
    }
}
