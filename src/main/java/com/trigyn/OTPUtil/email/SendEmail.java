package com.trigyn.OTPUtil.email;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.velocity.VelocityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.StringWriter;

/**
 * Sends an HTML email using a pre-resolved Velocity template body
 * over an existing JavaMail {@link Transport} connection.
 * 
 * @author devaj04@gmail.com
 */

@Slf4j
@Component
public class SendEmail {

    @Value("${notification.email.from}")
    private String fromEmail;

    /**
     * Sends an email to one or more recipients.
     *
     * @param emails    array of recipient email addresses
     * @param subject   email subject line
     * @param context   Velocity context (retained for logging / future use)
     * @param writer    StringWriter containing the fully-resolved HTML body
     * @param session   JavaMail session
     * @param transport active (connected) JavaMail transport
     * @throws MessagingException if the send operation fails
     */
    public void send(
            String[]        emails,
            String          subject,
            VelocityContext context,
            StringWriter    writer,
            Session         session,
            Transport       transport) throws MessagingException {

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(fromEmail));

        for (String email : emails) {
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(email.trim()));
        }

        message.setSubject(subject, "UTF-8");
        message.setContent(writer.toString(), "text/html; charset=UTF-8");

        transport.sendMessage(message, message.getAllRecipients());
        log.info("SendEmail: email successfully sent to {} recipient(s)", emails.length);
    }
}

