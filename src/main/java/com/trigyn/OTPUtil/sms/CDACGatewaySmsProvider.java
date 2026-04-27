package com.trigyn.OTPUtil.sms;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.security.KeyManagementException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CDAC National Informatics Centre (NIC) SMS Gateway provider.
 *
 * <p>Integrates with the Indian Government CDAC msdgweb gateway
 * ({@code https://msdgweb.mgov.gov.in/esms/sendsmsrequestDLT}).
 *
 * <p>Configuration is read from {@code application.properties}.
 * All properties must be configured in the properties file.
 *
 * <p>DLT (Distributed Ledger Technology) compliance:
 * Every SMS template sent via this gateway must be pre-registered with your
 * telecom operator.  Map each OTP {@code purpose} to its DLT template-id in
 * {@code application.properties} under {@code cdac.sms.dlt-templates.<PURPOSE>}.
 */
@Slf4j
@Component
public class CDACGatewaySmsProvider {

    // -----------------------------------------------------------------------
    //  Spring-injected configuration
    //  Environment-variable overrides are checked inside @PostConstruct init().
    // -----------------------------------------------------------------------

    @Value("${cdac.sms.base-url}")
    private String baseUrl;

    @Value("${cdac.sms.sender-id}")
    private String senderId;

    @Value("${cdac.sms.username}")
    private String userName;

    @Value("${cdac.sms.password}")
    private String password;

    @Value("${cdac.sms.dept-secure-key}")
    private String deptSecureKey;

    @Value("${cdac.sms.dlt-templates.signupOtp}")
    private String signupOtp;

    @Value("${cdac.sms.dlt-templates.resetPasswordWithOtp}")
    private String resetPasswordWithOtp;

    @Value("${cdac.sms.dlt-templates.otpContactUpdateTemplate}")
    private String otpContactUpdateTemplate;

    @Value("${cdac.sms.dlt-templates.deletePhone}")
    private String deletePhone;


    /**
     * Purpose → DLT Template-ID map.
     * Populated from {@code cdac.sms.dlt-templates.*} in application.properties.
     * Example:
     * <pre>
     * cdac.sms.dlt-templates.signupOtp=1007XXXXXXXX
     * cdac.sms.dlt-templates.resetPasswordWithOtp=1007XXXXXXXY
     * </pre>
     */
    @Value("#{${cdac.sms.dlt-templates:{}}}")
    private Map<String, String> dltTemplates;

    // -----------------------------------------------------------------------
    //  Lifecycle
    // -----------------------------------------------------------------------

    @PostConstruct
    public void init() {
        // Configuration is loaded from application.properties via @Value annotations above
        boolean valid = validateSettings();
        log.info("CDACGatewaySmsProvider initialised – configuration valid: {}", valid);
        log.info("CDACGatewaySmsProvider – base URL: {}", baseUrl);
        log.info("CDACGatewaySmsProvider – sender ID: {}", senderId);
        log.info("CDACGatewaySmsProvider – username: {}", userName);
        log.info("CDACGatewaySmsProvider – DLT templates configured: {}", 
                dltTemplates != null ? dltTemplates.size() : 0);
        if (dltTemplates != null) {
            dltTemplates.forEach((purpose, templateId) -> 
                    log.info("  → {} = {}", purpose, templateId));
        }
        if (!valid) {
            log.warn("CDACGatewaySmsProvider: one or more required settings are missing. "
                    + "SMS delivery will fail until configuration is corrected.");
        }
    }

    private boolean validateSettings() {
        boolean credsValid = StringUtils.isNotBlank(senderId)
                && StringUtils.isNotBlank(userName)
                && StringUtils.isNotBlank(password)
                && StringUtils.isNotBlank(deptSecureKey);
        
        // Check if DLT templates are configured with actual values (not placeholders)
        boolean dltValid = dltTemplates != null && !dltTemplates.isEmpty();
        if (dltValid) {
            for (String templateId : dltTemplates.values()) {
                if (templateId.contains("your-") || templateId.contains("XXXXXXXX")) {
                    log.warn("CDACGatewaySmsProvider – DLT template with placeholder value detected: {}. "
                            + "Replace with actual template ID from telecom operator.", templateId);
                    dltValid = false;
                }
            }
        }
        
        return credsValid && dltValid;
    }

    // -----------------------------------------------------------------------
    //  Public send API
    // -----------------------------------------------------------------------

    /**
     * Sends a text message to a single mobile number.
     *
     * @param mobileNumber target phone number (digits only, no country code prefix required)
     * @param smsText      plain-text message body
     * @param purpose      OTP purpose – used to resolve the DLT template-id
     * @return {@code true} if the gateway accepted the message
     */
    public boolean send(String mobileNumber, String smsText, String purpose) {
        return sendSms(mobileNumber, smsText, purpose);
    }

    /**
     * Sends a text message to multiple mobile numbers sequentially.
     *
     * @param mobileNumbers list of phone numbers
     * @param smsText       plain-text message body
     * @param purpose       OTP purpose
     * @return {@code true} if every send attempt returned success
     */
    public boolean send(List<String> mobileNumbers, String smsText, String purpose) {
        return mobileNumbers.stream()
                .allMatch(phone -> sendSms(phone, smsText, purpose));
    }

    // -----------------------------------------------------------------------
    //  Core send logic  (adapted from CDACGatewaySmsProvider v2)
    // -----------------------------------------------------------------------

    @SuppressWarnings({"deprecation"})
    private boolean sendSms(String mobileNumber, String smsText, String purpose) {
        String responseString = "";
        try {
            String dltTemplateId = signupOtp;
            log.info("CDACGatewaySmsProvider – dltTemplateId='{}' purpose='{}'", dltTemplateId, purpose);

            // TLS 1.2 context
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, null, null);
            SSLSocketFactory sf = new SSLSocketFactory(sslContext, SSLSocketFactory.STRICT_HOSTNAME_VERIFIER);
            Scheme scheme = new Scheme("https", 443, sf);

            @SuppressWarnings("resource")
            HttpClient client = new DefaultHttpClient();
            client.getConnectionManager().getSchemeRegistry().register(scheme);

            HttpPost post = new HttpPost(baseUrl);
            String encryptedPassword = sha1Hex(password);
            String hashKey = sha512HashGenerator(userName, senderId, smsText.trim(), deptSecureKey);

            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("bulkmobno",    mobileNumber));
            params.add(new BasicNameValuePair("senderid",     senderId));
            params.add(new BasicNameValuePair("content",      smsText.trim()));
            params.add(new BasicNameValuePair("smsservicetype", "bulkmsg"));
            params.add(new BasicNameValuePair("username",     userName));
            params.add(new BasicNameValuePair("password",     encryptedPassword));
            params.add(new BasicNameValuePair("key",          hashKey));
            params.add(new BasicNameValuePair("templateid",   dltTemplateId));

            post.setEntity(new UrlEncodedFormEntity(params));
            log.info("CDACGatewaySmsProvider – sending to mobile={}", mobileNumber);

            HttpResponse response = client.execute(post);

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(response.getEntity().getContent()))) {
                String line;
                StringBuilder sb = new StringBuilder();
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                responseString = sb.toString();
            }

            log.info("CDACGatewaySmsProvider – HTTP status: {}", response.getStatusLine().getStatusCode());
            log.info("CDACGatewaySmsProvider – gateway response: '{}'", responseString);

            // Parse gateway response
            // CDAC gateway returns XML/JSON response indicating success or failure
            // Success indicators: contains "1", "success", or "ok" (case-insensitive)
            // Failure indicators: contains "0", "error", "failed", "invalid" (case-insensitive)
            String lowerResponse = responseString.toLowerCase();
            
            if (lowerResponse.contains("error") || lowerResponse.contains("failed") || 
                lowerResponse.contains("invalid") || lowerResponse.contains("\"0\"") || 
                lowerResponse.contains("<status>0</status>")) {
                log.error("CDACGatewaySmsProvider – SMS delivery failed for mobile={}: response indicates error. Gateway response: {}", 
                        mobileNumber, responseString);
                return false;
            }
            
            if (StringUtils.isNotBlank(responseString)) {
                log.info("CDACGatewaySmsProvider – SMS accepted by gateway for mobile={}", mobileNumber);
                log.debug("CDACGatewaySmsProvider – detailed response for mobile={}: {}", mobileNumber, responseString);
                return true;
            } else {
                log.warn("CDACGatewaySmsProvider – empty response from gateway for mobile={}", mobileNumber);
                return false;
            }

        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            log.error("CDACGatewaySmsProvider – SSL/crypto error for mobile={}: {}", mobileNumber, e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("CDACGatewaySmsProvider – IO error for mobile={}: {}", mobileNumber, e.getMessage(), e);
            return false;
        }
    }

    // -----------------------------------------------------------------------
    //  DLT Template resolution
    // -----------------------------------------------------------------------

    /**
     * Resolves the DLT-registered template-id for the given OTP purpose.
     *
     * <p>Lookup order:
     * <ol>
     *   <li>Exact match on {@code purpose} key (case-insensitive)</li>
     *   <li>First template whose key is a substring of {@code smsText} (content-based fallback)</li>
     *   <li>Value of key {@code DEFAULT} if present</li>
     *   <li>Empty string (gateway will reject — configure templates in application.properties)</li>
     * </ol>
     */
    private String resolveDltTemplateId(String purpose, String smsText) {
        if (dltTemplates == null || dltTemplates.isEmpty()) {
            log.info("CDACGatewaySmsProvider – dltTemplates map is empty. Configure cdac.sms.dlt-templates.*");
            return "";
        }
        // 1. Exact purpose match (case-insensitive)
        for (Map.Entry<String, String> entry : dltTemplates.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(purpose)) {
                return entry.getValue();
            }
        }
        // 2. Content-based fallback: key appears in smsText
        for (Map.Entry<String, String> entry : dltTemplates.entrySet()) {
            if (smsText != null && smsText.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        // 3. DEFAULT fallback
        String defaultTemplate = dltTemplates.get("DEFAULT");
        if (StringUtils.isNotBlank(defaultTemplate)) {
            log.warn("CDACGatewaySmsProvider – using DEFAULT template for purpose='{}'", purpose);
            return defaultTemplate;
        }
        log.error("CDACGatewaySmsProvider – no DLT template found for purpose='{}'. SMS will likely fail.", purpose);
        return "";
    }

    // -----------------------------------------------------------------------
    //  Cryptographic helpers
    // -----------------------------------------------------------------------

    /**
     * Computes the SHA-1 hex digest of {@code text} encoded as ISO-8859-1.
     * Used to hash the gateway password before transmission.
     */
    private static String sha1Hex(String text)
            throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update(text.getBytes("iso-8859-1"), 0, text.length());
        return bytesToHex(md.digest());
    }

    /**
     * Generates the per-request HMAC-style hash key required by the CDAC gateway.
     *
     * <p>Formula: {@code SHA-512( userName + senderId + content + secureKey )}
     */
    protected static String sha512HashGenerator(
            String userName, String senderId, String content, String secureKey) {
        String input = userName.trim() + senderId.trim() + content.trim() + secureKey.trim();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.update(input.getBytes());
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-512 algorithm not available", e);
            throw new IllegalStateException("SHA-512 algorithm not available", e);
        }
    }

    /**
     * Converts a byte array to a lowercase hex string.
     */
    private static String bytesToHex(byte[] data) {
        StringBuilder buf = new StringBuilder(data.length * 2);
        for (byte b : data) {
            int high = (b >>> 4) & 0x0F;
            int low  = b & 0x0F;
            buf.append((char) (high <= 9 ? '0' + high : 'a' + high - 10));
            buf.append((char) (low  <= 9 ? '0' + low  : 'a' + low  - 10));
        }
        return buf.toString();
    }
}

