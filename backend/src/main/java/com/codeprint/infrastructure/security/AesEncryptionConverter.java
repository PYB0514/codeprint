// GitHub Access Token 등 민감 문자열을 AES-256-GCM으로 암호화하는 JPA 컨버터
package com.codeprint.infrastructure.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Component
@Converter
public class AesEncryptionConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private final SecretKeySpec secretKey;

    // base64로 인코딩된 32바이트 암호화 키를 주입받아 초기화
    public AesEncryptionConverter(@Value("${encryption.key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("Encryption key must be exactly 32 bytes (256-bit)");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    // 평문 → AES-GCM 암호화 후 Base64(IV + ciphertext) 반환
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(attribute.getBytes());
            byte[] combined = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, combined, IV_LENGTH, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Token encryption failed", e);
        }
    }

    // Base64(IV + ciphertext) → AES-GCM 복호화 후 평문 반환
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(dbData);
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted));
        } catch (Exception e) {
            // 구버전 미암호화 토큰(gho_xxx) — null 반환 후 다음 로그인 시 재암호화됨
            return null;
        }
    }
}
