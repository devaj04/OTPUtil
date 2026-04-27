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
 * Cassandra entity mapping to otp_transactions table in sunbird keyspace.
 *
 * CREATE TABLE sunbird.otp_transactions (
 *   type text,
 *   key text,
 *   attemptedcount int,
 *   maxcount int,
 *   createdon timestamp,
 *   lastmodifiedtime timestamp,
 *   otp text,
 *   referencenumber text,
 *   purpose text,
 *   PRIMARY KEY (key)
 * );
 */
@Table("otp_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpTransaction {

    /** The unique identifier: email address or phone number */
    @PrimaryKey("key")
    private String key;

    /** Channel type: email or phone */
    @Column("type")
    private String type;

    /** Number of failed verification attempts */
    @Column("attemptedcount")
    private int attemptedCount;

    /** Maximum allowed attempts before OTP is invalidated */
    @Column("maxcount")
    private int maxCount;

    /** Record creation timestamp (epoch millis stored as Instant) */
    @Column("createdon")
    private Instant createdOn;

    /** Last update timestamp */
    @Column("lastmodifiedtime")
    private Instant lastModifiedTime;

    /** AES-256-GCM encrypted OTP value */
    @Column("otp")
    private String otp;

    /** UUID reference number returned to the caller */
    @Column("referencenumber")
    private String referenceNumber;

    /** Business purpose for which OTP was generated (e.g., REGISTRATION) */
    @Column("purpose")
    private String purpose;
}

