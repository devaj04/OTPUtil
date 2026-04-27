package com.trigyn.OTPUtil.repository;

import com.trigyn.OTPUtil.entity.OtpRateLimit;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for accessing OTP rate limit records from Cassandra.
 */
@Repository
public interface OtpRateLimitRepository extends CassandraRepository<OtpRateLimit, String> {

    /**
     * Find the rate limit record for a given key.
     *
     * @param key the email address or phone number
     * @return OtpRateLimit if found, empty Optional otherwise
     */
    Optional<OtpRateLimit> findByKey(String key);
}

