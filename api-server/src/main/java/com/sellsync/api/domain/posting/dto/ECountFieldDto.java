package com.sellsync.api.domain.posting.dto;

import com.sellsync.api.domain.posting.enums.ECountField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Arrays;

/**
 * 이카운트 필드 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ECountFieldDto {
    
    private String fieldCode;        // 필드 코드 (예: "IO_DATE")
    private String fieldNameKr;      // 한글명 (예: "판매일자")
    private String fieldType;        // 데이터 타입 (TEXT, NUMBER, DATE 등)
    private boolean required;        // 필수 여부
    private String fieldLevel;       // 레벨 (HEADER, LINE)
    private String description;      // 설명
    private String exampleValue;     // 예시 값
    
    /**
     * ECountField Enum → DTO 변환
     */
    public static ECountFieldDto from(ECountField field) {
        return ECountFieldDto.builder()
            .fieldCode(field.getFieldCode())
            .fieldNameKr(field.getFieldNameKr())
            .fieldType(field.getFieldType().name())
            .required(field.isRequired())
            .fieldLevel(field.getFieldLevel().name())
            .description(field.getDescription())
            .exampleValue(field.getExampleValue())
            .build();
    }
    
    /**
     * 모든 이카운트 필드 목록 조회
     */
    public static List<ECountFieldDto> getAllFields() {
        return Arrays.stream(ECountField.values())
            .map(ECountFieldDto::from)
            .collect(Collectors.toList());
    }
    
    /**
     * 필수 필드만 조회
     */
    public static List<ECountFieldDto> getRequiredFields() {
        return Arrays.stream(ECountField.values())
            .filter(ECountField::isRequired)
            .map(ECountFieldDto::from)
            .collect(Collectors.toList());
    }
    
    /**
     * 레벨별 필드 조회
     */
    public static List<ECountFieldDto> getFieldsByLevel(ECountField.FieldLevel level) {
        return Arrays.stream(ECountField.values())
            .filter(f -> f.getFieldLevel() == level)
            .map(ECountFieldDto::from)
            .collect(Collectors.toList());
    }
}
