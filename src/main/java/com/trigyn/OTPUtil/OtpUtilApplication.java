package com.trigyn.OTPUtil;

/**
 * @author devaj04@gmail.com
 */

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * OTP Microservice – entry point.
 * Capabilities:
 *  - Input sanitization & validation
 *  - OTP generation with configurable length and purpose-based gating
 *  - Email and SMS delivery
 *  - AES-256-GCM encrypted OTP storage in Cassandra (with TTL)
 *  - Redis cache for fast lookup and TTL management
 *  - Attempt-count tracking with max-attempt enforcement
 *  - Auto-deletion of OTP on successful verification or TTL expiry
 */
@SpringBootApplication
@EnableScheduling
public class OtpUtilApplication {

    public static void main(String[] args) {
        SpringApplication.run(OtpUtilApplication.class, args);
    }
}
