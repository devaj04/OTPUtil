package com.trigyn.OTPUtil.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * @author devaj04@gmail.com
 */

/**
 * Request DTO for OTP generation.
 *
 * If purpose is null or empty, it defaults to signup OTP (configured via otp.default.purpose).
 */
@Data
public class GenerateOtpRequest {

    @NotBlank(message = "key must not be blank")
    private String key;

    @NotBlank(message = "type must not be blank")
    @Pattern(regexp = "email|phone", message = "type must be 'email' or 'phone'")
    private String type;

    // Purpose is optional - if null/empty, defaults to signup OTP
    private String purpose;
}
