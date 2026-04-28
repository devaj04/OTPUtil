package com.trigyn.OTPUtil.util;

/**
 * @author devaj04@gmail.com
 */

import com.trigyn.OTPUtil.exception.OtpException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM utility for encrypting/decrypting OTP values.
 *
 * Format stored in DB:  Base64( IV[12 bytes] + CipherText + Tag[16 bytes] )
 */
@Slf4j
@Component
public class EncryptionUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH   = 12;  // 96-bit IV recommended for GCM
    private static final int GCM_TAG_LENGTH  = 128; // bits

    private final SecretKeySpec secretKeySpec;

    public EncryptionUtil(@Value("${otp.encryption.secret}") String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        // Ensure exactly 32 bytes (AES-256)
        keyBytes = Arrays.copyOf(keyBytes, 32);
        this.secretKeySpec = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypts plain-text OTP and returns Base64-encoded cipher string.
     */
    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to cipher bytes
            byte[] combined = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw OtpException.encryptionError();
        }
    }

    /**
     * Decrypts a Base64-encoded cipher string back to plain-text OTP.
     */
    public String decrypt(String cipherText) {
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);

            byte[] iv         = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
            byte[] cipherBytes = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw OtpException.encryptionError();
        }
    }
}
