-- ============================================================
-- OTP Microservice – Cassandra DDL
-- Keyspace: sunbird
-- Timezone: IST (Indian Standard Time, UTC+5:30)
-- All timestamp fields (createdon, lastmodifiedtime, timestamp) are
-- stored and retrieved in IST as configured in application.properties
-- ============================================================

-- Create keyspace (adjust replication strategy for production)
CREATE KEYSPACE IF NOT EXISTS sunbird
    WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}
    AND durable_writes = true;

USE sunbird;

-- OTP transactions table
-- TTL is managed at the application layer via INSERT ... USING TTL
-- Timestamps are automatically converted to IST (Asia/Kolkata) on serialization/deserialization
CREATE TABLE IF NOT EXISTS sunbird.otp_transactions (
    key              text,           -- PRIMARY KEY: email address or phone number
    type             text,           -- 'email' | 'phone'
    otp              text,           -- AES-256-GCM encrypted OTP value
    referencenumber  text,           -- UUID returned to the caller (acts as a correlation ID)
    purpose          text,           -- Business purpose (e.g., REGISTRATION, LOGIN)
    attemptedcount   int,            -- Number of failed verification attempts
    maxcount         int,            -- Maximum allowed attempts
    createdon        timestamp,      -- Record creation time (stored in IST)
    lastmodifiedtime timestamp,      -- Last update time (stored in IST)
    PRIMARY KEY (key)
);

-- Secondary index on referencenumber to support lookups by referenceId if needed
CREATE INDEX IF NOT EXISTS idx_otp_referencenumber
    ON sunbird.otp_transactions (referencenumber);


-- OTP rate limit table
-- Tracks OTP generation attempts per key within a 1-hour window
-- TTL is managed at the application layer via INSERT ... USING TTL 3600
-- Timestamps are automatically converted to IST (Asia/Kolkata) on serialization/deserialization
CREATE TABLE IF NOT EXISTS sunbird.otp_rate_limit (
    key             text,           -- PRIMARY KEY: email address or phone number
    attemptedcount  int,            -- Number of OTP generation requests within this hour
    maxcount        int,            -- Maximum allowed OTP generations per hour (e.g., 3)
    timestamp       timestamp,      -- When this rate limit record was created/updated (stored in IST)
    PRIMARY KEY (key)
);

-- Automatic cleanup: TTL of 3600 seconds (1 hour) ensures records expire without manual cleanup
-- This allows a fresh set of attempts after 1 hour


