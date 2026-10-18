package com.example.ecommerce.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.ecommerce.entites.EmailOtp;
import com.example.ecommerce.enums.OtpPurpose;

public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long> {

    Optional<EmailOtp> findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(String email, OtpPurpose purpose);

    Optional<EmailOtp> findTopByEmailIgnoreCaseAndPurposeAndConsumedFalseOrderByCreatedAtDesc(String email, OtpPurpose purpose);

    /** Issuing a new code invalidates every older unused code for the same email and purpose. */
    @Modifying
    @Query("update EmailOtp o set o.consumed = true where lower(o.email) = lower(:email) and o.purpose = :purpose and o.consumed = false")
    int invalidateActive(@Param("email") String email, @Param("purpose") OtpPurpose purpose);
}
