package com.trigyn.OTPUtil.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * Status block included in every API response.
 */
@Data
@Builder
public class StatusDto {
    private String code;
    private String message;
}

