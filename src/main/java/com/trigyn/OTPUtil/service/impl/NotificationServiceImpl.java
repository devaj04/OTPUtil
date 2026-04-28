package com.trigyn.OTPUtil.service.impl;

import com.trigyn.OTPUtil.email.EmailConnection;
import com.trigyn.OTPUtil.email.EmailTemplateService;
import com.trigyn.OTPUtil.email.SendEmail;
import com.trigyn.OTPUtil.service.NotificationService;
import com.trigyn.OTPUtil.sms.CDACGatewaySmsProvider;
import com.trigyn.OTPUtil.util.ProjectUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Notification service implementation.
 *
 * – Email : Velocity-templated HTML email sent via persistent SMTP transport
 * – SMS   : CDAC NIC Gateway (msdgweb.mgov.gov.in)
 *
 * Notification failures are logged but do NOT throw exceptions.
 * OTP generation succeeds even if notification delivery fails.
 * The `otpSent` field in the response indicates actual delivery status.
 * 
 * @author devaj04@gmail.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final CDACGatewaySmsProvider cdacGatewaySmsProvider;
    private final EmailConnection        connection;
    private final EmailTemplateService   emailTemplateService;
    private final SendEmail              sendEmail;

    @Value("${email.subject.signupOtp}")
    private String signupOtpSubject;

    @Value("${email.subject.resetPasswordWithOtp}")
    private String resetPasswordWithOtpSubject;

    @Value("${email.subject.otpContactUpdateTemplate}")
    private String otpContactUpdateTemplateSubject;

    @Value("${email.subject.deleteUserAccountTemplate}")
    private String deleteUserAccountTemplateSubject;

    @Value("${otp.ttl.seconds:60}")
    private long ttlSeconds;

    @Value("${notification.email.reset-interval-ms:60000}")
    private String resetInterval;

    private long timer = 0L;

    // -----------------------------------------------------------------------
    // Email  –  Velocity templated HTML via persistent SMTP transport
    // -----------------------------------------------------------------------

    @Override
    public boolean sendEmail(String toEmail, String otp, String purpose) {
        String subject = getSubject(purpose);
        try {
            // Build template variables map
            Map<String, Object> request = new HashMap<>();
            request.put("otp",      otp);
            request.put("purpose",  purpose);
            request.put("validFor", ttlSeconds);
            request.put("subject", subject);

            // Resolve the Velocity template for this purpose
            String template = emailTemplateService.getTemplate(purpose);
            sendMail(request, List.of(toEmail), template);
            return true;
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage(), e);
            return false;
        }
    }

    private String getSubject(String purpose) {
        String subject = null;
        if(purpose.equalsIgnoreCase("signupOtp")){
            subject = signupOtpSubject;
        } else if(purpose.equalsIgnoreCase("resetPasswordWithOtp")){
            subject = resetPasswordWithOtpSubject;
        } else if(purpose.equalsIgnoreCase("otpContactUpdateTemplate")){
            subject = otpContactUpdateTemplateSubject;
        } else if(purpose.equalsIgnoreCase("deleteUserAccountTemplate")){
            subject = deleteUserAccountTemplateSubject;
        }
        return subject;
    }

    /**
     * Resolves the Velocity template and sends the email via the
     * managed SMTP transport, resetting the connection when needed.
     */
    private void sendMail(Map<String, Object> request, List<String> emails, String template) {
        try {
            // Init Velocity engine
            VelocityEngine velocityEngine = new VelocityEngine();
            velocityEngine.init();

            VelocityContext context = ProjectUtil.getContext(request);
            StringWriter writer = new StringWriter();
            velocityEngine.evaluate(context, writer, "OtpEmailTemplate", template);

            // Determine reset interval
            long interval = 60_000L;
            if (StringUtils.isNotBlank(resetInterval)) {
                interval = Long.parseLong(resetInterval);
            }

            // Reset connection if: never connected, interval elapsed, or transport dropped
            if (connection.getTransport() == null
                    || (System.currentTimeMillis() - timer) >= interval
                    || !connection.getTransport().isConnected()) {
                connection.resetConnection();
                timer = System.currentTimeMillis();
            }

            sendEmail.send(
                    emails.toArray(new String[0]),
                    (String) request.get("subject"),
                    context,
                    writer,
                    connection.getSession(),
                    connection.getTransport());

            log.info("OTP email sent successfully to {}", emails);
        } catch (Exception e) {
            log.error("sendMail: Exception occurred with message = {}", e.getMessage(), e);
            throw new RuntimeException("Email send failed: " + e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    // SMS  –  CDAC NIC Gateway (msdgweb.mgov.gov.in)
    // -----------------------------------------------------------------------

    @Override
    public boolean sendSms(String toPhone, String otp, String purpose) {
        try {
            String smsText = buildSmsBody(otp, purpose);
            boolean sent = cdacGatewaySmsProvider.send(toPhone, smsText, purpose);
            if (!sent) {
                log.warn("CDAC gateway returned failure for mobile={}", toPhone);
                return false;
            }
            log.info("OTP SMS dispatched successfully to={}", toPhone);
            return true;
        } catch (Exception e) {
            log.error("Failed to send OTP SMS to {}: {}", toPhone, e.getMessage(), e);
            return false;
        }
    }

    private String buildSmsBody(String otp, String purpose) {

        if(purpose.equalsIgnoreCase("signupOtp")){
            return String.format(
                    "OTP to verify your phone number on DIKSHA is %s. This is valid for %d seconds only.",
                    otp, ttlSeconds);
        } else if(purpose.equalsIgnoreCase("resetPasswordWithOtp")){
            return String.format(
                    "OTP to reset your password on DIKSHA is %s. This is valid for %d seconds only.",
                     otp, ttlSeconds);
        } else if(purpose.equalsIgnoreCase("otpContactUpdateTemplate")){
            return String.format(
                    "DIKSHA: Use OTP %s to edit the contact details for your DIKSHA profile.",
                     otp);
        } else if(purpose.equalsIgnoreCase("1107175041055648550")){
            return String.format(
                    "Use OTP %s to delete your DIKSHA account. This is valid for %d seconds only.",
                    otp, ttlSeconds);
        }
        return null;
    }
}
