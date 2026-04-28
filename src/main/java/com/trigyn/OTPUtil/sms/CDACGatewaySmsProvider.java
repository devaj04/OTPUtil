package com.trigyn.OTPUtil.sms;

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
 * 
 * @author devaj04@gmail.com
 */
@Slf4j
@Component
public class CDACGatewaySmsProvider {

    @Value("${sms.base-url}")
    private String baseUrl;

    @Value("${sms.sender-id}")
    private String senderId;

    @Value("${sms.username}")
    private String userName;

    @Value("${sms.password}")
    private String password;

    @Value("${sms.dept-secure-key}")
    private String deptSecureKey;

    @Value("${sms.dlt-templates.signupOtp}")
    private String signupOtp;

    @Value("${sms.dlt-templates.resetPasswordWithOtp}")
    private String resetPasswordWithOtp;

    @Value("${sms.dlt-templates.otpContactUpdateTemplate}")
    private String otpContactUpdateTemplate;

    @Value("${sms.dlt-templates.deleteUserAccountPhone}")
    private String deleteUserAccountPhone;

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

    @SuppressWarnings({"deprecation"})
    private boolean sendSms(String mobileNumber, String smsText, String purpose) {
        String responseString = "";
        try {
            String dltTemplateId = resolveDltTemplateId(purpose);
            log.warn("📤 SMS SENDING - mobile={} | purpose={} | template={} | smsText={}", mobileNumber, purpose, dltTemplateId, smsText);

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

            String lowerResponse = responseString.toLowerCase();
            
            // Check for explicit failure indicators
            if (lowerResponse.contains("error") || 
                lowerResponse.contains("failed") || 
                lowerResponse.contains("invalid") || 
                lowerResponse.contains("\"0\"") || 
                lowerResponse.contains("<status>0</status>") ||
                lowerResponse.contains("whitelisted") ||  // IP not whitelisted
                lowerResponse.contains("not authorized") ||
                lowerResponse.contains("authentication failed") ||
                lowerResponse.contains("invalid template") ||
                lowerResponse.contains("blocked") ||
                lowerResponse.contains("suspended")) {
                log.error("📊 SMS DELIVERY FAILED - mobile={} | gateway_response='{}'", mobileNumber, responseString);
                return false;
            }
            
            // Check for explicit success indicators
            if (lowerResponse.contains("success") || 
                lowerResponse.contains("accepted") ||
                lowerResponse.contains("submitted") ||
                lowerResponse.contains("\"1\"") || 
                lowerResponse.contains("<status>1</status>") ||
                lowerResponse.contains("ok")) {
                log.info("📊 SMS ACCEPTED - mobile={} | gateway_response='{}'", mobileNumber, responseString);
                return true;
            }
            
            // If response is blank, it's a failure
            if (StringUtils.isBlank(responseString)) {
                log.warn("📊 SMS FAILED - mobile={} | empty_response", mobileNumber);
                return false;
            }
            
            // For any other non-empty response without explicit success keywords, treat as failure
            log.warn("📊 SMS FAILED - mobile={} | ambiguous_response='{}' | treating_as_failure", mobileNumber, responseString);
            return false;

        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            log.error("❌ SMS ERROR - mobile={} | type=SSL/Crypto | error={}", mobileNumber, e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("❌ SMS ERROR - mobile={} | type=IO | error={}", mobileNumber, e.getMessage(), e);
            return false;
        }
    }

    private String resolveDltTemplateId(String purpose) {
        if(purpose.equalsIgnoreCase("signupOtp")){
            return signupOtp;
        } else if(purpose.equalsIgnoreCase("resetPasswordWithOtp")){
            return resetPasswordWithOtp;
        } else if(purpose.equalsIgnoreCase("otpContactUpdateTemplate")){
            return otpContactUpdateTemplate;
        } else if(purpose.equalsIgnoreCase("deleteUserAccountPhone")){
            return deleteUserAccountPhone;
        } else {
            log.warn("⚠️ DLT TEMPLATE NOT FOUND - purpose={} | hint=check_configuration", purpose);
            return "";
        }
    }

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