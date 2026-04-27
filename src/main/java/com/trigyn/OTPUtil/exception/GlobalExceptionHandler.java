package com.trigyn.OTPUtil.exception;

import com.trigyn.OTPUtil.dto.response.ApiResponse;
import com.trigyn.OTPUtil.dto.response.StatusDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Centralised exception handler – returns consistent ApiResponse envelopes.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OtpException.class)
    public ResponseEntity<ApiResponse<Void>> handleOtpException(OtpException ex) {
        log.warn("OTP business exception [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity
                .status(ex.getHttpStatus())
                .body(ApiResponse.<Void>builder()
                        .status(StatusDto.builder()
                                .code(ex.getErrorCode())
                                .message(ex.getMessage())
                                .build())
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation failure: {}", msg);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Void>builder()
                        .status(StatusDto.builder()
                                .code("OTP_400")
                                .message(msg)
                                .build())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.<Void>builder()
                        .status(StatusDto.builder()
                                .code("OTP_500")
                                .message("An unexpected error occurred. Please contact support.")
                                .build())
                        .build());
    }
}

