package com.trigyn.OTPUtil.controller;

import com.trigyn.OTPUtil.dto.request.GenerateOtpRequestEnvelope;
import com.trigyn.OTPUtil.dto.request.VerifyOtpRequestEnvelope;
import com.trigyn.OTPUtil.dto.response.ApiResponse;
import com.trigyn.OTPUtil.dto.response.GenerateOtpResponseBody;
import com.trigyn.OTPUtil.dto.response.StatusDto;
import com.trigyn.OTPUtil.dto.response.VerifyOtpResponseBody;
import com.trigyn.OTPUtil.service.OtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing OTP generation and verification endpoints.
 * Base path: /api/v1/otp
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/otp")
@RequiredArgsConstructor
public class OtpController {

    private final OtpService otpService;

    /**
     * Generates an OTP for the given key and purpose, then delivers it via email or SMS according to the specified type.
     *
     * Request body with explicit purpose:
     * {
     *   "request": {
     *     "key": "user@example.com",
     *     "type": "email",
     *     "purpose": "resetPasswordWithOtp"
     *   }
     * }
     *
     * Request without purpose (treated as signup OTP):
     * {
     *   "request": {
     *     "key": "devajdildar1@gmail.com",
     *     "type": "email"
     *   }
     * }
     *
     * Or for phone:
     * {
     *   "request": {
     *     "key": "1234567890",
     *     "type": "phone"
     *   }
     * }
     */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<GenerateOtpResponseBody>> generateOtp(
            @Valid @RequestBody GenerateOtpRequestEnvelope envelope) {

        log.info("OTP generation request received for key={}, type={}, purpose={}",
                envelope.getRequest().getKey(),
                envelope.getRequest().getType(),
                envelope.getRequest().getPurpose());

        GenerateOtpResponseBody responseBody = otpService.generateOtp(envelope.getRequest());

        // Determine status based on whether OTP was actually sent
        String statusCode;
        String statusMessage;

        if ("yes".equalsIgnoreCase(responseBody.getOtpSent())) {
            statusCode = "OTP_200";
            statusMessage = "OTP generated and sent successfully.";
            log.info("OTP sent successfully for key={}", envelope.getRequest().getKey());
        } else {
            // OTP was generated but notification failed (e.g., SMTP/SMS provider error)
            statusCode = "OTP_206";
            statusMessage = "OTP generated but failed to send notification. Please retry.";
            log.warn("OTP generated but notification failed for key={}", envelope.getRequest().getKey());
        }

        ApiResponse<GenerateOtpResponseBody> response = ApiResponse.<GenerateOtpResponseBody>builder()
                .response(responseBody)
                .status(StatusDto.builder()
                        .code(statusCode)
                        .message(statusMessage)
                        .build())
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Verifies the OTP submitted by the user.
     * Request body:
     * {
     *   "request": {
     *     "key": "user@example.com",
     *     "type": "email",
     *     "otp": "123456",
     *     "referenceId": "uuid-returned-during-generate"
     *   }
     * }
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<VerifyOtpResponseBody>> verifyOtp(
            @Valid @RequestBody VerifyOtpRequestEnvelope envelope) {

        log.info("OTP verification request received for key={}, type={}",
                envelope.getRequest().getKey(),
                envelope.getRequest().getType());

        VerifyOtpResponseBody responseBody = otpService.verifyOtp(envelope.getRequest());

        ApiResponse<VerifyOtpResponseBody> response = ApiResponse.<VerifyOtpResponseBody>builder()
                .response(responseBody)
                .status(StatusDto.builder()
                        .code("OTP_200")
                        .message("OTP verified successfully.")
                        .build())
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}

