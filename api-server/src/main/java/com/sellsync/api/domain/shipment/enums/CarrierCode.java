package com.sellsync.api.domain.shipment.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum CarrierCode {
    // 주요 택배사
    CJ("CJGLS", "CJ대한통운", "04"),
    HANJIN("HANJIN", "한진택배", "05"),
    LOTTE("LOTTE", "롯데택배", "08"),
    LOGEN("LOGEN", "로젠택배", "06"),
    POST("EPOST", "우체국택배", "01"),
    
    // 기타 택배사
    KGB("KGB", "KGB택배", ""),
    DAESIN("DAESIN", "대신택배", ""),
    ILYANG("ILYANG", "일양로지스", ""),
    CHUNIL("CHUNIL", "천일택배", ""),
    HDEXP("HDEXP", "합동택배", ""),
    
    // 직접배송
    DIRECT("DIRECT", "직접배송", "99");

    private final String code;
    private final String name;
    private final String naverCode;  // 스마트스토어용 코드

    CarrierCode(String code, String name, String naverCode) {
        this.code = code;
        this.name = name;
        this.naverCode = naverCode;
    }

    public static Optional<CarrierCode> fromCode(String code) {
        return Arrays.stream(values())
                .filter(c -> c.code.equalsIgnoreCase(code))
                .findFirst();
    }

    public static Optional<CarrierCode> fromName(String name) {
        return Arrays.stream(values())
                .filter(c -> c.name.contains(name) || name.contains(c.name))
                .findFirst();
    }

    public static CarrierCode resolve(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("택배사 정보가 없습니다");
        }

        String normalized = input.trim().toUpperCase();
        
        // 코드로 찾기
        Optional<CarrierCode> byCode = fromCode(normalized);
        if (byCode.isPresent()) return byCode.get();

        // 이름으로 찾기
        Optional<CarrierCode> byName = fromName(input.trim());
        if (byName.isPresent()) return byName.get();

        // 부분 매칭
        if (normalized.contains("CJ") || normalized.contains("대한통운")) return CJ;
        if (normalized.contains("한진")) return HANJIN;
        if (normalized.contains("롯데")) return LOTTE;
        if (normalized.contains("로젠")) return LOGEN;
        if (normalized.contains("우체국") || normalized.contains("POST")) return POST;

        throw new IllegalArgumentException("알 수 없는 택배사: " + input);
    }
}
