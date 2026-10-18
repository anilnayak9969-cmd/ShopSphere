package com.example.ecommerce.services.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.ecommerce.entites.EmailOtp;
import com.example.ecommerce.enums.OtpPurpose;
import com.example.ecommerce.exception.ApiException;
import com.example.ecommerce.repositories.EmailOtpRepository;
import com.example.ecommerce.services.EmailService;

@ExtendWith(MockitoExtension.class)
class OtpServiceImplTest {

    private static final String EMAIL = "riya@example.com";

    @Mock
    private EmailOtpRepository otpRepository;

    @Mock
    private EmailService emailService;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // low cost keeps tests fast
    private OtpServiceImpl otpService;

    @BeforeEach
    void setUp() {
        otpService = new OtpServiceImpl(otpRepository, encoder, emailService, 10, 5, 60);
    }

    @Test
    void sendStoresOnlyHashAndEmailsTheCode() {
        when(otpRepository.findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(EMAIL, OtpPurpose.REGISTRATION))
                .thenReturn(Optional.empty());

        otpService.sendOtp(EMAIL, "Riya", OtpPurpose.REGISTRATION);

        ArgumentCaptor<EmailOtp> saved = ArgumentCaptor.forClass(EmailOtp.class);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpRepository).save(saved.capture());
        verify(emailService).sendOtpEmail(eq(EMAIL), eq("Riya"), code.capture(), eq(OtpPurpose.REGISTRATION), eq(10));
        assertThat(code.getValue()).matches("\\d{6}");
        assertThat(saved.getValue().getOtpHash()).isNotEqualTo(code.getValue());
        assertThat(encoder.matches(code.getValue(), saved.getValue().getOtpHash())).isTrue();
        verify(otpRepository).invalidateActive(EMAIL, OtpPurpose.REGISTRATION);
    }

    @Test
    void resendWithinCooldownIsRejected() {
        EmailOtp recent = otp("123456", LocalDateTime.now().plusMinutes(10));
        recent.setCreatedAt(LocalDateTime.now().minusSeconds(10));
        when(otpRepository.findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(EMAIL, OtpPurpose.REGISTRATION))
                .thenReturn(Optional.of(recent));

        assertThatThrownBy(() -> otpService.sendOtp(EMAIL, "Riya", OtpPurpose.REGISTRATION))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Please wait");
        verify(emailService, never()).sendOtpEmail(anyString(), anyString(), anyString(), any(), eq(10));
    }

    @Test
    void correctCodeIsConsumed() {
        EmailOtp otp = otp("482913", LocalDateTime.now().plusMinutes(5));
        stubActive(otp);

        otpService.verifyOtp(EMAIL, OtpPurpose.REGISTRATION, "482913");

        assertThat(otp.isConsumed()).isTrue();
    }

    @Test
    void wrongCodeIncrementsAttempts() {
        EmailOtp otp = otp("482913", LocalDateTime.now().plusMinutes(5));
        stubActive(otp);

        assertThatThrownBy(() -> otpService.verifyOtp(EMAIL, OtpPurpose.REGISTRATION, "000000"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("4 attempt(s) left");
        assertThat(otp.getAttempts()).isEqualTo(1);
        assertThat(otp.isConsumed()).isFalse();
    }

    @Test
    void expiredCodeIsRejected() {
        EmailOtp otp = otp("482913", LocalDateTime.now().minusSeconds(1));
        stubActive(otp);

        assertThatThrownBy(() -> otpService.verifyOtp(EMAIL, OtpPurpose.REGISTRATION, "482913"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("OTP_EXPIRED"));
        assertThat(otp.isConsumed()).isTrue();
    }

    @Test
    void codeIsLockedAfterMaxAttempts() {
        EmailOtp otp = otp("482913", LocalDateTime.now().plusMinutes(5));
        otp.setAttempts(5);
        stubActive(otp);

        assertThatThrownBy(() -> otpService.verifyOtp(EMAIL, OtpPurpose.REGISTRATION, "482913"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("OTP_LOCKED"));
    }

    private void stubActive(EmailOtp otp) {
        when(otpRepository.findTopByEmailIgnoreCaseAndPurposeAndConsumedFalseOrderByCreatedAtDesc(EMAIL,
                OtpPurpose.REGISTRATION)).thenReturn(Optional.of(otp));
    }

    private EmailOtp otp(String code, LocalDateTime expiresAt) {
        EmailOtp otp = new EmailOtp();
        otp.setEmail(EMAIL);
        otp.setPurpose(OtpPurpose.REGISTRATION);
        otp.setOtpHash(encoder.encode(code));
        otp.setExpiresAt(expiresAt);
        return otp;
    }
}
