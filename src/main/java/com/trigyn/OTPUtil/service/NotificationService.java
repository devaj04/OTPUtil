package com.trigyn.OTPUtil.service;

/**
 * Notification service contract for delivering OTPs.
 * 
 * Note: Send methods do NOT throw exceptions on failure.
 * They return true if notification was successfully sent, false otherwise.
 * 
 * @author devaj04@gmail.com
 */
public interface NotificationService {

    /**
     * Sends an OTP via email.
     *
     * @param toEmail  recipient email address
     * @param otp      plain-text OTP value
     * @param purpose  business purpose (used in message body)
     * @return true if email was sent successfully, false if send failed
     */
    boolean sendEmail(String toEmail, String otp, String purpose);

    /**
     * Sends an OTP via SMS.
     *
     * @param toPhone  recipient phone number (E.164 or local format)
     * @param otp      plain-text OTP value
     * @param purpose  business purpose (used in message body)
     * @return true if SMS was sent successfully, false if send failed
     */
    boolean sendSms(String toPhone, String otp, String purpose);
}

