package com.trigyn.OTPUtil.util;

import com.trigyn.OTPUtil.exception.OtpException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Sanitises and validates inbound request fields before processing.
 */
@Slf4j
@Component
public class InputValidator {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");

    // Accepts 7-15 digit phone numbers optionally prefixed with +
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^\\+?[0-9]{7,15}$");

    private final List<String> validPurposes;
    private final String defaultPurpose;

    public InputValidator(
            @Value("${otp.valid.purposes}") String purposes,
            @Value("${otp.default.purpose}") String defaultPurpose) {
        // Don't uppercase - keep purposes as-is (they come in camelCase from requests)
        this.validPurposes = Arrays.asList(purposes.split(",\\s*"));
        this.defaultPurpose = defaultPurpose;
        log.info("InputValidator initialised with {} valid purposes. Default purpose: {}",
                validPurposes.size(), defaultPurpose);
    }

    /**
     * Sanitises the key value: trims whitespace and lowercases email addresses.
     */
    public String sanitiseKey(String key, String type) {
        if (key == null) {
            throw OtpException.invalidInput("'key' must not be null");
        }
        String sanitised = key.trim();
        if ("email".equalsIgnoreCase(type)) {
            sanitised = sanitised.toLowerCase();
        }
        return sanitised;
    }

    /**
     * Validates that the key matches the expected format for its type.
     */
    public void validateKey(String key, String type) {
        if ("email".equalsIgnoreCase(type)) {
            if (!EMAIL_PATTERN.matcher(key).matches()) {
                throw OtpException.invalidInput("Invalid email address format: " + key);
            }
        } else if ("phone".equalsIgnoreCase(type)) {
            if (!PHONE_PATTERN.matcher(key).matches()) {
                throw OtpException.invalidInput("Invalid phone number format: " + key);
            }
        } else {
            throw OtpException.invalidInput("Unsupported type '" + type + "'. Must be 'email' or 'phone'.");
        }
    }

    /**
     * Sanitises purpose to canonical form (trimmed).
     * If null or blank, defaults to the configured default purpose (signup OTP).
     * 
     * @param purpose the purpose from the request (may be null)
     * @return the sanitised purpose, or the default purpose if null/empty
     */
    public String sanitisePurpose(String purpose) {
        // Handle null or empty purpose → use signup default
        if (purpose == null) {
            log.info("Purpose is null, defaulting to signup: {}", defaultPurpose);
            return defaultPurpose;
        }
        if (purpose.trim().isEmpty()) {
            log.info("Purpose is empty, defaulting to signup: {}", defaultPurpose);
            return defaultPurpose;
        }
        return purpose.trim();
    }

    /**
     * Sanitises purpose with context awareness (e.g., based on request type).
     * Currently just delegates to sanitisePurpose, but can be extended for
     * contextual defaults like different defaults for email vs phone.
     */
    public String sanitisePurpose(String purpose, String type) {
        // Future: can implement type-aware defaults here
        // e.g., if type.equals("phone") && purpose is null → use phone-specific default
        return sanitisePurpose(purpose);
    }

    /**
     * Validates that the given purpose is in the predefined allowed list.
     */
    public void validatePurpose(String purpose) {
        if (!validPurposes.contains(purpose)) {
            throw OtpException.invalidPurpose(purpose);
        }
    }

    /**
     * Basic check that the OTP string contains only digits.
     */
    public void validateOtpFormat(String otp) {
        if (otp == null || !otp.matches("\\d+")) {
            throw OtpException.invalidInput("OTP must contain digits only.");
        }
    }
}

