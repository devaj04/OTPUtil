package com.trigyn.OTPUtil.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;

/**
 * Cassandra entity for OTP rate limiting.
 * Tracks how many OTP generation requests were made per key within the current hour.
 *
 * CREATE TABLE sunbird.otp_rate_limit (
 *   key             text,
 *   attemptedcount  int,
 *   maxcount        int,
 *   timestamp       timestamp,
 *   PRIMARY KEY (key)
 * ) WITH default_time_to_live = 3600;
 */
@Table("otp_rate_limit")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpRateLimit {

    /** The unique identifier: email address or phone number (primary key) */
    @PrimaryKey("key")
    private String key;

    /** Number of OTP generation attempts within the current hour */
    @Column("attemptedcount")
    private int attemptedCount;

    /** Maximum OTP generations allowed per hour */
    @Column("maxcount")
    private int maxCount;

    /** Timestamp when this rate limit record was created/updated */
    @Column("timestamp")
    private Instant timestamp;
}

