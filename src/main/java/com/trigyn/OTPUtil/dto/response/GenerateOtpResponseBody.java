package com.trigyn.OTPUtil.dto.response;

/**
 * @author devaj04@gmail.com
 */

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * Response body for OTP generation.
 */
@Data
@Builder
public class GenerateOtpResponseBody {

    @JsonProperty("otpSent")
    private String otpSent;

    @JsonProperty("referenceId")
    private String referenceId;

    @JsonProperty("generatedTs")
    private long generatedTs;

    @JsonProperty("validFor")
    private long validFor;
}
