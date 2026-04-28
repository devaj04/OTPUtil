package com.trigyn.OTPUtil.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author devaj04@gmail.com
 */

/**
 * Outer envelope: { "request": { ... } }
 */
@Data
public class GenerateOtpRequestEnvelope {

    @NotNull(message = "request body must not be null")
    @Valid
    private GenerateOtpRequest request;
}

