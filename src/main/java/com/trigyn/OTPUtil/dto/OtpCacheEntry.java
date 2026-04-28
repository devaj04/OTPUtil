package com.trigyn.OTPUtil.dto;

/**
 * @author devaj04@gmail.com
 */

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Value stored in Redis for active OTP sessions.
 * Key pattern: OTP:{key}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpCacheEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Encrypted OTP value */
    private String encryptedOtp;

    /** UUID reference number */
    private String referenceNumber;

    /** Business purpose */
    private String purpose;

    /** Channel type: email or phone */
    private String type;

    /** Epoch millis when OTP was generated */
    private long generatedTs;

    /** Number of failed attempts */
    private int attemptedCount;

    /** Max allowed attempts */
    private int maxCount;
}
