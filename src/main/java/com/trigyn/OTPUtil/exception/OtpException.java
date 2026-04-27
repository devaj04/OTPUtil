package com.trigyn.OTPUtil.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Business exception for all OTP-related failures.
 */
@Getter
public class OtpException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String errorCode;

    public OtpException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    // ---- Convenience factory methods ----

    public static OtpException invalidInput(String message) {
        return new OtpException(message, "OTP_400", HttpStatus.BAD_REQUEST);
    }

    public static OtpException invalidPurpose(String purpose) {
        return new OtpException(
                "Invalid or unsupported purpose: " + purpose, "OTP_401", HttpStatus.BAD_REQUEST);
    }

    public static OtpException otpExpired() {
        return new OtpException("OTP has expired. Please request a new one.", "OTP_410", HttpStatus.GONE);
    }

    public static OtpException otpInvalid() {
        return new OtpException("OTP is invalid.", "OTP_422", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static OtpException maxAttemptsExceeded() {
        return new OtpException(
                "Maximum OTP verification attempts exceeded. Please request a new OTP.",
                "OTP_429", HttpStatus.TOO_MANY_REQUESTS);
    }

    public static OtpException activeOtpExists(long remainingTtlSeconds) {
        return new OtpException(
                "An OTP has already been sent. Please verify it or wait " + remainingTtlSeconds
                        + " seconds for it to expire before requesting a new one.",
                "OTP_409", HttpStatus.CONFLICT);
    }

    public static OtpException rateLimitExceeded() {
        return new OtpException(
                "Maximum OTP generation attempts (3) exceeded per hour. Please try again in 1 hour.",
                "OTP_429", HttpStatus.TOO_MANY_REQUESTS);
    }

    public static OtpException notificationFailed(String channel) {
        return new OtpException(
                "Failed to send OTP via " + channel + ". Please try again.",
                "OTP_503", HttpStatus.SERVICE_UNAVAILABLE);
    }

    public static OtpException encryptionError() {
        return new OtpException("Internal encryption error.", "OTP_500", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

