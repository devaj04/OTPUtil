package com.trigyn.OTPUtil.service.impl;

import com.trigyn.OTPUtil.dto.OtpCacheEntry;
import com.trigyn.OTPUtil.dto.request.GenerateOtpRequest;
import com.trigyn.OTPUtil.dto.request.VerifyOtpRequest;
import com.trigyn.OTPUtil.dto.response.GenerateOtpResponseBody;
import com.trigyn.OTPUtil.dto.response.VerifyOtpResponseBody;
import com.trigyn.OTPUtil.entity.OtpRateLimit;
import com.trigyn.OTPUtil.entity.OtpTransaction;
import com.trigyn.OTPUtil.exception.OtpException;
import com.trigyn.OTPUtil.repository.OtpRateLimitRepository;
import com.trigyn.OTPUtil.repository.OtpTransactionRepository;
import com.trigyn.OTPUtil.service.NotificationService;
import com.trigyn.OTPUtil.service.OtpService;
import com.trigyn.OTPUtil.util.EncryptionUtil;
import com.trigyn.OTPUtil.util.InputValidator;
import com.trigyn.OTPUtil.util.OtpGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.data.cassandra.core.InsertOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Core OTP service implementation.
 *
 * Flow:
 *  Generate:
 *    1. Sanitise + validate input
 *    2. Generate random OTP
 *    3. Encrypt OTP
 *    4. Persist to Cassandra (with TTL) + Redis (with TTL)
 *    5. Send notification (email or SMS)
 *    6. Return response
 *
 *  Verify:
 *    1. Sanitise + validate input
 *    2. Fetch OTP from Redis (fast path). If absent → expired
 *    3. Match referenceId
 *    4. Check attempt count
 *    5. Decrypt and compare OTP
 *    6. On success / max attempts → delete from Redis + Cassandra
 *    7. Return result
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private static final String REDIS_KEY_PREFIX = "OTP:";

    private final OtpTransactionRepository otpTransactionRepository;
    private final OtpRateLimitRepository otpRateLimitRepository;
    private final CassandraOperations cassandraOperations;
    private final RedisTemplate<String, OtpCacheEntry> redisTemplate;
    private final NotificationService notificationService;
    private final OtpGenerator otpGenerator;
    private final EncryptionUtil encryptionUtil;
    private final InputValidator inputValidator;

    @Value("${otp.ttl.seconds:60}")
    private long ttlSeconds;

    @Value("${otp.max.attempts:3}")
    private int maxAttempts;

    @Value("${otp.ratelimit.max-per-hour:3}")
    private int rateLimitMaxPerHour;

    @Value("${otp.ratelimit.window-seconds:3600}")
    private long rateLimitWindowSeconds;

    // -----------------------------------------------------------------------
    //  Generate OTP
    // -----------------------------------------------------------------------

    @Override
    public GenerateOtpResponseBody generateOtp(GenerateOtpRequest request) {
        // 1. Sanitise & validate
        String sanitisedKey = inputValidator.sanitiseKey(request.getKey(), request.getType());
        inputValidator.validateKey(sanitisedKey, request.getType());
        String canonicalPurpose = inputValidator.sanitisePurpose(request.getPurpose());
        inputValidator.validatePurpose(canonicalPurpose);

        // 1a. Check rate limit (max 3 OTP generations per hour per key)
        checkAndUpdateRateLimit(sanitisedKey);

        // 1b. Guard: reject if an unexpired OTP already exists for this key
        String rKey = redisKey(sanitisedKey);
        OtpCacheEntry existingEntry = redisTemplate.opsForValue().get(rKey);
        if (existingEntry != null) {
            Long remainingTtl = redisTemplate.getExpire(rKey);
            long remaining = (remainingTtl != null && remainingTtl > 0) ? remainingTtl : ttlSeconds;
            log.warn("Active OTP already exists for key={}, referenceId={}, remainingTTL={}s",
                    sanitisedKey, existingEntry.getReferenceNumber(), remaining);
            throw OtpException.activeOtpExists(remaining);
        }

        // 2. Generate raw OTP
        String rawOtp = otpGenerator.generate();
        log.info("Generated OTP for key={}, type={}, otp={}", sanitisedKey, request.getType(), rawOtp);

        // 3. Encrypt
        String encryptedOtp = encryptionUtil.encrypt(rawOtp);

        // 4. Prepare metadata
        String referenceNumber = UUID.randomUUID().toString();
        Instant now = Instant.now();
        long generatedTs = now.toEpochMilli();

        // 5. Persist to Cassandra with TTL
        OtpTransaction txn = OtpTransaction.builder()
                .key(sanitisedKey)
                .type(request.getType())
                .otp(encryptedOtp)
                .referenceNumber(referenceNumber)
                .purpose(canonicalPurpose)
                .attemptedCount(0)
                .maxCount(maxAttempts)
                .createdOn(now)
                .lastModifiedTime(now)
                .build();

        InsertOptions insertOptions = InsertOptions.builder()
                .ttl(Duration.ofSeconds(ttlSeconds))
                .build();
        cassandraOperations.insert(txn, insertOptions);
        log.info("OTP transaction saved to Cassandra for key={}", sanitisedKey);

        // 6. Cache in Redis with TTL
        OtpCacheEntry cacheEntry = OtpCacheEntry.builder()
                .encryptedOtp(encryptedOtp)
                .referenceNumber(referenceNumber)
                .purpose(canonicalPurpose)
                .type(request.getType())
                .generatedTs(generatedTs)
                .attemptedCount(0)
                .maxCount(maxAttempts)
                .build();
        redisTemplate.opsForValue().set(redisKey(sanitisedKey), cacheEntry, Duration.ofSeconds(ttlSeconds));
        log.info("OTP cached in Redis for key={} with TTL={}s", sanitisedKey, ttlSeconds);

        // 7. Send notification
        boolean notificationSent = sendNotification(request.getType(), sanitisedKey, rawOtp, canonicalPurpose);

        return GenerateOtpResponseBody.builder()
                .otpSent(notificationSent ? "yes" : "no")
                .referenceId(referenceNumber)
                .generatedTs(generatedTs)
                .validFor(ttlSeconds)
                .build();
    }

    // -----------------------------------------------------------------------
    //  Verify OTP
    // -----------------------------------------------------------------------

    @Override
    public VerifyOtpResponseBody verifyOtp(VerifyOtpRequest request) {
        // 1. Sanitise & validate
        String sanitisedKey = inputValidator.sanitiseKey(request.getKey(), request.getType());
        inputValidator.validateKey(sanitisedKey, request.getType());
        inputValidator.validateOtpFormat(request.getOtp());

        String redisKey = redisKey(sanitisedKey);

        // 2. Fetch from Redis
        OtpCacheEntry cachedEntry = redisTemplate.opsForValue().get(redisKey);
        if (cachedEntry == null) {
            log.warn("OTP not found in Redis for key={} – likely expired", sanitisedKey);
            // Clean up Cassandra if still present
            silentDeleteFromCassandra(sanitisedKey);
            throw OtpException.otpExpired();
        }

        // 3. Validate referenceId
        if (!cachedEntry.getReferenceNumber().equals(request.getReferenceId())) {
            log.warn("ReferenceId mismatch for key={}", sanitisedKey);
            throw OtpException.invalidInput("Invalid referenceId provided.");
        }

        // 4a. Check max attempts BEFORE comparing
        if (cachedEntry.getAttemptedCount() >= cachedEntry.getMaxCount()) {
            log.warn("Max attempts already exceeded for key={}", sanitisedKey);
            deleteOtp(sanitisedKey, redisKey);
            throw OtpException.maxAttemptsExceeded();
        }

        // 5. Decrypt and compare
        String decryptedOtp = encryptionUtil.decrypt(cachedEntry.getEncryptedOtp());
        boolean otpMatches = decryptedOtp.equals(request.getOtp().trim());

        if (otpMatches) {
            log.info("OTP verified successfully for key={}", sanitisedKey);
            deleteOtp(sanitisedKey, redisKey);
            return VerifyOtpResponseBody.builder()
                    .otpValidated("yes")
                    .referenceId(request.getReferenceId())
                    .build();
        }

        // 4b. Increment attempt count
        int newAttemptCount = cachedEntry.getAttemptedCount() + 1;

        if (newAttemptCount >= cachedEntry.getMaxCount()) {
            log.warn("Max OTP attempts exceeded for key={}", sanitisedKey);
            deleteOtp(sanitisedKey, redisKey);
            throw OtpException.maxAttemptsExceeded();
        }

        // Rebuild the cache entry with updated attempt count
        OtpCacheEntry updatedEntry = OtpCacheEntry.builder()
                .encryptedOtp(cachedEntry.getEncryptedOtp())
                .referenceNumber(cachedEntry.getReferenceNumber())
                .purpose(cachedEntry.getPurpose())
                .type(cachedEntry.getType())
                .generatedTs(cachedEntry.getGeneratedTs())
                .attemptedCount(newAttemptCount)
                .maxCount(cachedEntry.getMaxCount())
                .build();

        // Update attempt count in Redis (preserve remaining TTL)
        Long remainingTtl = redisTemplate.getExpire(redisKey);
        long ttl = (remainingTtl != null && remainingTtl > 0) ? remainingTtl : ttlSeconds;
        redisTemplate.opsForValue().set(redisKey, updatedEntry, Duration.ofSeconds(ttl));
        log.info("Updated Redis cache for key={} with attemptedCount={}/{}", sanitisedKey, newAttemptCount, cachedEntry.getMaxCount());

        // Update attempt count in Cassandra asynchronously (best-effort)
        updateCassandraAttemptCount(sanitisedKey, newAttemptCount);

        log.warn("Invalid OTP attempt {}/{} for key={}", newAttemptCount, cachedEntry.getMaxCount(), sanitisedKey);
        throw OtpException.otpInvalid();
    }

    // -----------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------

    private boolean sendNotification(String type, String key, String otp, String purpose) {
        try {
            if ("email".equalsIgnoreCase(type)) {
                return notificationService.sendEmail(key, otp, purpose);
            } else {
                return notificationService.sendSms(key, otp, purpose);
            }
        } catch (Exception e) {
            log.error("Unexpected error during notification for type={}, key={}: {}", type, key, e.getMessage(), e);
            return false;
        }
    }

    private void deleteOtp(String cassandraKey, String redisKey) {
        try {
            redisTemplate.delete(redisKey);
            log.debug("OTP deleted from Redis for key={}", cassandraKey);
        } catch (Exception e) {
            log.warn("Failed to delete OTP from Redis for key={}: {}", cassandraKey, e.getMessage());
        }
        silentDeleteFromCassandra(cassandraKey);
    }

    private void silentDeleteFromCassandra(String key) {
        try {
            otpTransactionRepository.deleteByKey(key);
            log.debug("OTP deleted from Cassandra for key={}", key);
        } catch (Exception e) {
            log.warn("Failed to delete OTP from Cassandra for key={}: {}", key, e.getMessage());
        }
    }

    private void updateCassandraAttemptCount(String key, int newAttemptCount) {
        try {
            Optional<OtpTransaction> optional = otpTransactionRepository.findByKey(key);
            if (optional.isPresent()) {
                OtpTransaction txn = optional.get();
                txn.setAttemptedCount(newAttemptCount);
                txn.setLastModifiedTime(Instant.now());
                // Use explicit update instead of save to ensure Cassandra is updated
                cassandraOperations.update(txn);
                log.info("Cassandra updated: key={}, new attemptedCount={}", key, newAttemptCount);
            } else {
                log.warn("updateCassandraAttemptCount: OTP record not found in Cassandra for key={}", key);
            }
        } catch (Exception e) {
            log.error("Failed to update attempt count in Cassandra for key={}: {}", key, e.getMessage(), e);
        }
    }

    private String redisKey(String key) {
        return REDIS_KEY_PREFIX + key;
    }

    // -----------------------------------------------------------------------
    //  Rate Limiting
    // -----------------------------------------------------------------------

    /**
     * Checks and updates the rate limit for OTP generation.
     * Maximum allowed: 3 OTP generations per hour per key.
     *
     * @param key the email or phone identifier
     * @throws OtpException if rate limit exceeded
     */
    private void checkAndUpdateRateLimit(String key) {
        try {
            Optional<OtpRateLimit> existingLimit = otpRateLimitRepository.findByKey(key);

            if (existingLimit.isPresent()) {
                OtpRateLimit rateLimit = existingLimit.get();
                log.info("Rate limit check: key={}, currentCount={}, maxAllowed={}",
                        key, rateLimit.getAttemptedCount(), rateLimitMaxPerHour);

                // If already at max, reject
                if (rateLimit.getAttemptedCount() >= rateLimitMaxPerHour) {
                    log.warn("Rate limit exceeded for key={}, attempted={}, max={}",
                            key, rateLimit.getAttemptedCount(), rateLimitMaxPerHour);
                    throw OtpException.rateLimitExceeded();
                }

                // Increment attempt count
                rateLimit.setAttemptedCount(rateLimit.getAttemptedCount() + 1);
                rateLimit.setTimestamp(Instant.now());
                cassandraOperations.update(rateLimit);
                log.info("Rate limit updated: key={}, newCount={}", key, rateLimit.getAttemptedCount());

            } else {
                // First request for this key in this hour - create new record
                OtpRateLimit newLimit = OtpRateLimit.builder()
                        .key(key)
                        .attemptedCount(1)
                        .maxCount(rateLimitMaxPerHour)
                        .timestamp(Instant.now())
                        .build();

                InsertOptions insertOptions = InsertOptions.builder()
                        .ttl(Duration.ofSeconds(rateLimitWindowSeconds))
                        .build();
                cassandraOperations.insert(newLimit, insertOptions);
                log.info("Rate limit created: key={}, maxAllowed={}, TTL={}s", key, rateLimitMaxPerHour, rateLimitWindowSeconds);
            }
        } catch (OtpException e) {
            throw e;  // Re-throw rate limit exceptions
        } catch (Exception e) {
            log.error("Error checking rate limit for key={}: {}", key, e.getMessage(), e);
            throw OtpException.encryptionError();  // Fail secure - treat as error
        }
    }
}

