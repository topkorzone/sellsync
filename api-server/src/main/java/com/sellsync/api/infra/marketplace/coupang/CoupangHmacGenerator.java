package com.sellsync.api.infra.marketplace.coupang;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 쿠팡 HMAC 서명 생성기
 */
@Component
@Slf4j
public class CoupangHmacGenerator {

    private static final String ALGORITHM = "HmacSHA256";
    private static final DateTimeFormatter DATE_FORMAT = 
            DateTimeFormatter.ofPattern("yyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    /**
     * 쿠팡 API Authorization 헤더 생성
     */
    public String generateAuthorization(
            CoupangCredentials credentials,
            String method,
            String path,
            String query) {
        
        String datetime = DATE_FORMAT.format(Instant.now());
        String message = buildMessage(datetime, method, path, query);
        String signature = sign(message, credentials.getSecretKey());

        return String.format(
                "CEA algorithm=%s, access-key=%s, signed-date=%s, signature=%s",
                ALGORITHM,
                credentials.getAccessKey(),
                datetime,
                signature
        );
    }

    /**
     * 서명 메시지 생성
     */
    private String buildMessage(String datetime, String method, String path, String query) {
        StringBuilder sb = new StringBuilder();
        sb.append(datetime).append(method).append(path);
        if (query != null && !query.isEmpty()) {
            sb.append(query);
        }
        return sb.toString();
    }

    /**
     * HMAC-SHA256 서명
     */
    private String sign(String message, String secretKey) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to generate HMAC signature", e);
        }
    }

    /**
     * 바이트 배열을 16진수 문자열로 변환
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
