package com.trigyn.OTPUtil.util;

/**
 * @author devaj04@gmail.com
 */

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates cryptographically-secure numeric OTPs of configurable length.
 */
@Component
public class OtpGenerator {

    private final int otpLength;
    private final SecureRandom random = new SecureRandom();

    public OtpGenerator(@Value("${otp.length:6}") int otpLength) {
        this.otpLength = otpLength;
    }

    /**
     * Generates a zero-padded numeric OTP of the configured length.
     */
    public String generate() {
        int bound = (int) Math.pow(10, otpLength);
        int otpValue = random.nextInt(bound);
        return String.format("%0" + otpLength + "d", otpValue);
    }
}
