package com.trigyn.OTPUtil.service;

/**
 * @author devaj04@gmail.com
 */

import com.trigyn.OTPUtil.dto.request.GenerateOtpRequest;
import com.trigyn.OTPUtil.dto.request.VerifyOtpRequest;
import com.trigyn.OTPUtil.dto.response.GenerateOtpResponseBody;
import com.trigyn.OTPUtil.dto.response.VerifyOtpResponseBody;

/**
 * Core OTP service contract.
 */
public interface OtpService {

    /**
     * Validates input, generates OTP, persists to DB+Redis, and sends notification.
     *
     * @param request validated generation request
     * @return generation response with referenceId and timestamps
     */
    GenerateOtpResponseBody generateOtp(GenerateOtpRequest request);

    /**
     * Verifies the supplied OTP against the stored value.
     * Deletes the OTP on success or after max attempts are exceeded.
     *
     * @param request validated verification request
     * @return verification result
     */
    VerifyOtpResponseBody verifyOtp(VerifyOtpRequest request);
}

