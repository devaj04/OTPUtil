package com.trigyn.OTPUtil.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * Response body for OTP verification.
 */
@Data
@Builder
public class VerifyOtpResponseBody {

    @JsonProperty("otpValidated")
    private String otpValidated;

    @JsonProperty("referenceId")
    private String referenceId;
}

