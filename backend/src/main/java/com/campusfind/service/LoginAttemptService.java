package com.campusfind.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory rate limiting for login attempts to prevent brute-force abuse
 * without introducing unsafe permanent database account lockouts.
 */
@Service
public class LoginAttemptService {

    public static final int MAX_ATTEMPTS = 5;
    public static final long LOCKOUT_DURATION_SECONDS = 300; // 5 minutes

    private static class AttemptRecord {
        int attempts;
        Instant firstAttemptTime;

        AttemptRecord(int attempts, Instant firstAttemptTime) {
            this.attempts = attempts;
            this.firstAttemptTime = firstAttemptTime;
        }
    }

    private final Map<String, AttemptRecord> attemptsCache = new ConcurrentHashMap<>();

    /**
     * Checks if the given key (IP address or email) is currently blocked.
     */
    public boolean isBlocked(String key) {
        if (key == null) return false;
        AttemptRecord record = attemptsCache.get(key);
        if (record == null) return false;

        Instant now = Instant.now();
        if (now.isAfter(record.firstAttemptTime.plusSeconds(LOCKOUT_DURATION_SECONDS))) {
            // Window expired, clean up
            attemptsCache.remove(key);
            return false;
        }

        return record.attempts >= MAX_ATTEMPTS;
    }

    /**
     * Records a failed login attempt for the key.
     */
    public void recordFailure(String key) {
        if (key == null) return;
        Instant now = Instant.now();
        attemptsCache.compute(key, (k, existing) -> {
            if (existing == null || now.isAfter(existing.firstAttemptTime.plusSeconds(LOCKOUT_DURATION_SECONDS))) {
                return new AttemptRecord(1, now);
            } else {
                existing.attempts += 1;
                return existing;
            }
        });
    }

    /**
     * Resets attempt count upon successful login.
     */
    public void recordSuccess(String key) {
        if (key != null) {
            attemptsCache.remove(key);
        }
    }

    /**
     * Clears all cached attempts (useful for testing).
     */
    public void resetAll() {
        attemptsCache.clear();
    }
}
