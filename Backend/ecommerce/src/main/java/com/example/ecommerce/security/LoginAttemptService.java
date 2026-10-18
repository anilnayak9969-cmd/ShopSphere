package com.example.ecommerce.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.example.ecommerce.exception.ApiException;

/**
 * Basic brute-force protection: after 5 wrong passwords for one email, logins for that email
 * are blocked for 15 minutes. Kept in memory, so it resets when the server restarts — fine for
 * a single server; a multi-server deployment would move this to Redis or the database.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private record Attempts(int failures, Instant lockedUntil) {
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public void checkNotBlocked(String email) {
        Attempts current = attempts.get(email);
        if (current != null && current.lockedUntil() != null && Instant.now().isBefore(current.lockedUntil())) {
            long minutes = Math.max(1, Duration.between(Instant.now(), current.lockedUntil()).toMinutes());
            throw ApiException.tooManyRequests(
                    "Too many failed attempts. Try again in " + minutes + " minute(s) or reset your password.");
        }
    }

    public void recordFailure(String email) {
        attempts.compute(email, (key, current) -> {
            int failures = (current == null || isExpiredLock(current)) ? 1 : current.failures() + 1;
            Instant lockedUntil = failures >= MAX_FAILURES ? Instant.now().plus(LOCK_DURATION) : null;
            return new Attempts(failures, lockedUntil);
        });
    }

    public void reset(String email) {
        attempts.remove(email);
    }

    private boolean isExpiredLock(Attempts a) {
        return a.lockedUntil() != null && Instant.now().isAfter(a.lockedUntil());
    }
}
