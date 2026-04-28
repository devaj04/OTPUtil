package com.trigyn.OTPUtil.email;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads Velocity email templates from {@code classpath:templates/email/}
 * and provides them by OTP purpose.
 */
@Slf4j
@Component
public class EmailTemplateService {

    /** Maps OTP purpose → Velocity template string */
    private final Map<String, String> templates = new HashMap<>();

    private static final String TEMPLATE_PATH = "templates/email/";

    @PostConstruct
    public void loadTemplates() {
        loadTemplate("signupOtp",                "signup-otp.vm");
        loadTemplate("resetPasswordWithOtp",     "reset-password-otp.vm");
        loadTemplate("otpContactUpdateTemplate", "contact-update-otp.vm");
        loadTemplate("deleteUserAccountTemplate","delete-account-otp.vm");
        log.info("EmailTemplateService: {} template(s) loaded", templates.size());
    }

    /**
     * Returns the Velocity template string for the given purpose.
     * Falls back to the default template if the purpose-specific one is not found.
     */
    public String getTemplate(String purpose) {
        String template = templates.get(purpose);
        if (template == null) {
            log.warn("EmailTemplateService: no template found for purpose='{}', using default", purpose);
            template = templates.get("signupOtp");
        }
        return template;
    }

    // -----------------------------------------------------------------------

    private void loadTemplate(String purpose, String fileName) {
        try {
            ClassPathResource resource = new ClassPathResource(TEMPLATE_PATH + fileName);
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            templates.put(purpose, content);
        } catch (IOException e) {
            log.error("EmailTemplateService: could not load template '{}': {}", fileName, e.getMessage());
        }
    }
}

