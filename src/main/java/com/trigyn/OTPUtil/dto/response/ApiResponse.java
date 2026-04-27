package com.trigyn.OTPUtil.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * Generic API envelope:  { "response": {...}, "status": {...} }
 */
@Data
@Builder
public class ApiResponse<T> {

    @JsonProperty("response")
    private T response;

    @JsonProperty("status")
    private StatusDto status;
}

