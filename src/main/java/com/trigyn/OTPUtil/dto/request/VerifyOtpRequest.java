package com.trigyn.OTPUtil.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Request DTO for OTP verification.
 */
@Data
public class VerifyOtpRequest {

    @NotBlank(message = "key must not be blank")
    private String key;

    @NotBlank(message = "type must not be blank")
    @Pattern(regexp = "email|phone", message = "type must be 'email' or 'phone'")
    private String type;

    @NotBlank(message = "otp must not be blank")
    private String otp;

    @NotBlank(message = "referenceId must not be blank")
    private String referenceId;
}

