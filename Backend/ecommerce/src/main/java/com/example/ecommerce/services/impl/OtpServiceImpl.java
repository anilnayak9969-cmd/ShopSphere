package com.example.ecommerce.services.impl;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.ecommerce.entites.EmailOtp;
import com.example.ecommerce.enums.OtpPurpose;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.EmailOtpRepository;
import com.example.ecommerce.services.EmailService;
import com.example.ecommerce.services.OtpService;

@Service
public class OtpServiceImpl implements OtpService {

    private final SecureRandom random = new SecureRandom();
    private final EmailOtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final int expiryMinutes;
    private final int maxAttempts;
    private final int resendCooldownSeconds;

    public OtpServiceImpl(EmailOtpRepository otpRepository, PasswordEncoder passwordEncoder, EmailService emailService,
                          @Value("${app.otp.expiry-minutes:10}") int expiryMinutes,
                          @Value("${app.otp.max-attempts:5}") int maxAttempts,
                          @Value("${app.otp.resend-cooldown-seconds:60}") int resendCooldownSeconds) {
        this.otpRepository = otpRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.expiryMinutes = expiryMinutes;
        this.maxAttempts = maxAttempts;
        this.resendCooldownSeconds = resendCooldownSeconds;
    }

    @Override
    @Transactional
    public void sendOtp(String email, String name, OtpPurpose purpose) {
        // Cooldown stops someone from flooding an inbox by pressing "resend" repeatedly.
        otpRepository.findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(email, purpose).ifPresent(last -> {
            long secondsSince = Duration.between(last.getCreatedAt(), LocalDateTime.now()).toSeconds();
            if (secondsSince < resendCooldownSeconds) {
                throw ApiException.tooManyRequests("Please wait " + (resendCooldownSeconds - secondsSince)
                        + " seconds before requesting a new code.");
            }
        });

        otpRepository.invalidateActive(email, purpose);

        String code = String.format("%06d", random.nextInt(1_000_000));
        EmailOtp otp = new EmailOtp();
        otp.setEmail(email);
        otp.setPurpose(purpose);
        otp.setOtpHash(passwordEncoder.encode(code));
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(expiryMinutes));
        otpRepository.save(otp);

        emailService.sendOtpEmail(email, name, code, purpose, expiryMinutes);
    }

    /**
     * Runs in its own transaction that is NOT rolled back on ApiException, so a wrong guess still
     * increases the attempt counter even though an error is returned to the user.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public void verifyOtp(String email, OtpPurpose purpose, String code) {
        EmailOtp otp = otpRepository.findTopByEmailIgnoreCaseAndPurposeAndConsumedFalseOrderByCreatedAtDesc(email, purpose)
                .orElseThrow(() -> ApiException.badRequest("OTP_INVALID",
                        "No active code found. Please request a new one."));

        if (LocalDateTime.now().isAfter(otp.getExpiresAt())) {
            otp.setConsumed(true);
            throw ApiException.badRequest("OTP_EXPIRED", "This code has expired. Please request a new one.");
        }
        if (otp.getAttempts() >= maxAttempts) {
            otp.setConsumed(true);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_LOCKED",
                    "Too many incorrect attempts. Please request a new code.");
        }
        if (!passwordEncoder.matches(code, otp.getOtpHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            int left = maxAttempts - otp.getAttempts();
            if (left <= 0) {
                otp.setConsumed(true);
            }
            throw ApiException.badRequest("OTP_INVALID", left > 0
                    ? "Incorrect code. " + left + " attempt(s) left."
                    : "Incorrect code. Please request a new one.");
        }
        otp.setConsumed(true);
    }
}
