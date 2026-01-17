package com.sellsync.api.infra.marketplace.coupang;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

/**
 * 쿠팡 인증 정보
 */
@Data
public class CoupangCredentials {
    private String vendorId;
    private String accessKey;
    private String secretKey;
    
    public static CoupangCredentials parse(String json) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(json, CoupangCredentials.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Coupang credentials", e);
        }
    }
}
