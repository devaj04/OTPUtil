package com.trigyn.OTPUtil.email;

/**
 * @author devaj04@gmail.com
 */

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.mail.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Manages the lifecycle of the SMTP {@link Session} and {@link Transport} connection.
 *
 * <p>Tracks the last reset time so callers can decide when to reconnect
 * (controlled by {@code notification.email.reset-interval-ms}).
 */
@Slf4j
@Component
public class EmailConnection {

    @Value("${spring.mail.host}")
    private String host;

    @Value("${spring.mail.port:587}")
    private int port;

    @Value("${spring.mail.username}")
    private String username;

    @Value("${spring.mail.password}")
    private String password;

    @Value("${spring.mail.properties.mail.smtp.auth:true}")
    private String smtpAuth;

    @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}")
    private String startTls;

    @Getter
    private Session session;

    @Getter
    private Transport transport;

    /** Epoch millis of the last successful connection reset. */
    @Getter
    private long lastResetTime = 0L;

    @PostConstruct
    public void init() {
        log.info("EmailConnection: initialising SMTP session for host={}", host);
        buildSession();
    }

    /**
     * Closes the existing transport (if open) and opens a fresh one.
     * Updates {@link #lastResetTime}.
     */
    public synchronized void resetConnection() {
        log.info("EmailConnection: resetting SMTP transport connection");
        closeTransport();
        buildSession();
        try {
            transport = session.getTransport("smtp");
            transport.connect(host, username, password);
            lastResetTime = System.currentTimeMillis();
            log.info("EmailConnection: SMTP transport connected successfully");
        } catch (MessagingException e) {
            log.error("EmailConnection: failed to connect SMTP transport: {}", e.getMessage(), e);
        }
    }

    // -----------------------------------------------------------------------
    //  Private
    // -----------------------------------------------------------------------

    @Value("${spring.mail.properties.mail.smtp.connectiontimeout:10000}")
    private String connectionTimeout;

    @Value("${spring.mail.properties.mail.smtp.timeout:10000}")
    private String readTimeout;

    @Value("${spring.mail.properties.mail.smtp.writetimeout:10000}")
    private String writeTimeout;

    // ...existing code...

    private void buildSession() {
        Properties props = new Properties();
        props.put("mail.smtp.host",             host);
        props.put("mail.smtp.port",             String.valueOf(port));
        props.put("mail.smtp.auth",             smtpAuth);
        props.put("mail.smtp.starttls.enable",  startTls);
        props.put("mail.smtp.starttls.required","true");
        props.put("mail.smtp.connectiontimeout", connectionTimeout);
        props.put("mail.smtp.timeout",           readTimeout);
        props.put("mail.smtp.writetimeout",      writeTimeout);

        session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
    }

    private void closeTransport() {
        if (transport != null && transport.isConnected()) {
            try {
                transport.close();
                log.debug("EmailConnection: SMTP transport closed");
            } catch (MessagingException e) {
                log.warn("EmailConnection: error closing SMTP transport: {}", e.getMessage());
            }
        }
    }

    @PreDestroy
    public void cleanup() {
        closeTransport();
    }
}

