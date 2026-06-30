// 파일 내용의 SHA-256 해시를 16진 문자열로 계산 — 파싱 캐시 키의 내용 변경 감지축
package com.codeprint.infrastructure.analysis;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class ContentHash {

    private ContentHash() {}

    // 바이트 배열의 SHA-256을 소문자 16진(64자) 문자열로 반환
    public static String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 미지원 환경", e);
        }
    }
}
