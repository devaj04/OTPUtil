package com.trigyn.OTPUtil.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Outer envelope: { "request": { ... } }
 */
@Data
public class VerifyOtpRequestEnvelope {

    @NotNull(message = "request body must not be null")
    @Valid
    private VerifyOtpRequest request;
}

