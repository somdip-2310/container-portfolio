package dev.somdip.containerplatform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * Service for encrypting and decrypting sensitive data like OAuth tokens.
 * Uses AES-256-GCM for authenticated encryption.
 */
@Service
public class EncryptionService {
    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;
    private static final int SALT_LENGTH_BYTE = 16;
    private static final int KEY_LENGTH = 256;
    private static final int ITERATION_COUNT = 65536;

    private final String encryptionKey;

    public EncryptionService(@Value("${encryption.key:${JWT_SECRET:DefaultEncryptionKey256Bit}}") String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }

    /**
     * Encrypts plaintext using AES-256-GCM
     * Returns Base64 encoded string containing: salt + IV + ciphertext
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return null;
        }

        try {
            // Generate random salt and IV
            byte[] salt = generateRandomBytes(SALT_LENGTH_BYTE);
            byte[] iv = generateRandomBytes(IV_LENGTH_BYTE);

            // Derive key from password
            SecretKey key = deriveKey(encryptionKey, salt);

            // Encrypt
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());

            // Combine salt + IV + ciphertext
            ByteBuffer buffer = ByteBuffer.allocate(salt.length + iv.length + ciphertext.length);
            buffer.put(salt);
            buffer.put(iv);
            buffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(buffer.array());

        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypts Base64 encoded ciphertext
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return null;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedText);

            // Extract salt, IV, and ciphertext
            ByteBuffer buffer = ByteBuffer.wrap(decoded);

            byte[] salt = new byte[SALT_LENGTH_BYTE];
            buffer.get(salt);

            byte[] iv = new byte[IV_LENGTH_BYTE];
            buffer.get(iv);

            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            // Derive key from password
            SecretKey key = deriveKey(encryptionKey, salt);

            // Decrypt
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext);

        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new RuntimeException("Decryption failed", e);
        }
    }

    private SecretKey deriveKey(String password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    private byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }
}
