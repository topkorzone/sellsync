package com.sellsync.api.domain.posting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 이카운트 판매전표(SaveSale) API 필드 정의
 * 
 * 이카운트 API 문서 기준으로 모든 필드를 정의
 * 사용자는 이 중 필요한 필드만 선택하여 템플릿 구성
 */
@Getter
@RequiredArgsConstructor
public enum ECountField {
    
    // ========== 헤더 필드 (전표 레벨) ==========
    
    /**
     * 판매일자 (필수) - STRING(8)
     * 예: "20260115"
     */
    IO_DATE("IO_DATE", "판매일자", FieldType.DATE, true, FieldLevel.HEADER, 
        "판매일자 (YYYYMMDD 형식)", "20260115"),
    
    /**
     * 거래처코드 (필수) - STRING(30)
     * ERP에 등록된 거래처 코드
     */
    CUST("CUST", "거래처코드", FieldType.TEXT, true, FieldLevel.HEADER,
        "ERP에 등록된 거래처 코드 (필수)", null),
    
    /**
     * 거래처명 - STRING(50)
     * 거래처 이름
     */
    CUST_DES("CUST_DES", "거래처명", FieldType.TEXT, false, FieldLevel.HEADER,
        "거래처 이름", null),
    
    /**
     * 담당자 - STRING(30)
     */
    EMP_CD("EMP_CD", "담당자", FieldType.TEXT, false, FieldLevel.HEADER,
        "담당자 코드", null),
    
    /**
     * 출하창고 (필수) - STRING(5)
     */
    WH_CD("WH_CD", "출하창고", FieldType.TEXT, true, FieldLevel.HEADER,
        "출하창고 코드 (필수)", null),
    
    /**
     * 구분(거래유형) - STRING(2)
     * Self-Customizing > 환경설정 > 기능설정 > 재고관리 > 수불단위 설정
     */
    IO_TYPE("IO_TYPE", "구분(거래유형)", FieldType.TEXT, false, FieldLevel.HEADER,
        "거래 유형 코드", null),
    
    /**
     * 외화종류 - STRING(5)
     */
    EXCHANGE_TYPE("EXCHANGE_TYPE", "외화종류", FieldType.TEXT, false, FieldLevel.HEADER,
        "외화 통화 코드 (USD, JPY 등)", null),
    
    /**
     * 환율 - NUMERIC(18,4)
     */
    EXCHANGE_RATE("EXCHANGE_RATE", "환율", FieldType.NUMBER, false, FieldLevel.HEADER,
        "외화 환율", null),
    
    /**
     * 부서 - STRING(100)
     */
    SITE("SITE", "부서", FieldType.TEXT, false, FieldLevel.HEADER,
        "부서 코드", null),
    
    /**
     * 프로젝트 - STRING(14)
     */
    PJT_CD("PJT_CD", "프로젝트", FieldType.TEXT, false, FieldLevel.HEADER,
        "프로젝트 코드", null),
    
    /**
     * 판매No - STRING(30)
     * Self-Customizing > 환경설정 > 사용환경설정 > 전표번호
     */
    DOC_NO("DOC_NO", "판매No", FieldType.TEXT, false, FieldLevel.HEADER,
        "판매 문서번호", null),
    
    /**
     * 제목 - STRING(200)
     */
    TTL_CTT("TTL_CTT", "제목", FieldType.TEXT, false, FieldLevel.HEADER,
        "전표 제목", null),
    
    /**
     * 문자필드1 - STRING(200)
     */
    U_MEMO1("U_MEMO1", "문자필드1", FieldType.TEXT, false, FieldLevel.HEADER,
        "사용자 정의 문자 필드 1", null),
    
    /**
     * 문자필드2 - STRING(200)
     */
    U_MEMO2("U_MEMO2", "문자필드2", FieldType.TEXT, false, FieldLevel.HEADER,
        "사용자 정의 문자 필드 2", null),
    
    /**
     * 문자필드3 - STRING(200)
     */
    U_MEMO3("U_MEMO3", "문자필드3", FieldType.TEXT, false, FieldLevel.HEADER,
        "사용자 정의 문자 필드 3", null),
    
    /**
     * 문자필드4 - STRING(200)
     */
    U_MEMO4("U_MEMO4", "문자필드4", FieldType.TEXT, false, FieldLevel.HEADER,
        "사용자 정의 문자 필드 4", null),
    
    /**
     * 문자필드5 - STRING(200)
     */
    U_MEMO5("U_MEMO5", "문자필드5", FieldType.TEXT, false, FieldLevel.HEADER,
        "사용자 정의 문자 필드 5", null),
    
    /**
     * 추가문자필드사1~10 - STRING(200)
     */
    ADD_TXT_01_T("ADD_TXT_01_T", "추가문자필드사1", FieldType.TEXT, false, FieldLevel.HEADER,
        "추가 문자 필드 1", null),
    
    ADD_TXT_02_T("ADD_TXT_02_T", "추가문자필드사2", FieldType.TEXT, false, FieldLevel.HEADER,
        "추가 문자 필드 2", null),
    
    ADD_TXT_03_T("ADD_TXT_03_T", "추가문자필드사3", FieldType.TEXT, false, FieldLevel.HEADER,
        "추가 문자 필드 3", null),
    
    ADD_TXT_04_T("ADD_TXT_04_T", "추가문자필드사4", FieldType.TEXT, false, FieldLevel.HEADER,
        "추가 문자 필드 4", null),
    
    ADD_TXT_05_T("ADD_TXT_05_T", "추가문자필드사5", FieldType.TEXT, false, FieldLevel.HEADER,
        "추가 문자 필드 5", null),
    
    ADD_TXT_06_T("ADD_TXT_06_T", "추가문자필드사6", FieldType.TEXT, false, FieldLevel.HEADER,
        "추가 문자 필드 6", null),
    
    ADD_TXT_07_T("ADD_TXT_07_T", "추가문자필드사7", FieldType.TEXT, false, FieldLevel.HEADER,
        "추가 문자 필드 7", null),
    
    ADD_TXT_08_T("ADD_TXT_08_T", "추가문자필드사8", FieldType.TEXT, false, FieldLevel.HEADER,
        "추가 문자 필드 8", null),
    
    ADD_TXT_09_T("ADD_TXT_09_T", "추가문자필드사9", FieldType.TEXT, false, FieldLevel.HEADER,
        "추가 문자 필드 9", null),
    
    ADD_TXT_10_T("ADD_TXT_10_T", "추가문자필드사10", FieldType.TEXT, false, FieldLevel.HEADER,
        "추가 문자 필드 10", null),
    
    /**
     * 추가숫자필드사1~5 - NUMERIC(28,10)
     */
    ADD_NUM_01_T("ADD_NUM_01_T", "추가숫자필드사1", FieldType.NUMBER, false, FieldLevel.HEADER,
        "추가 숫자 필드 1", null),
    
    ADD_NUM_02_T("ADD_NUM_02_T", "추가숫자필드사2", FieldType.NUMBER, false, FieldLevel.HEADER,
        "추가 숫자 필드 2", null),
    
    ADD_NUM_03_T("ADD_NUM_03_T", "추가숫자필드사3", FieldType.NUMBER, false, FieldLevel.HEADER,
        "추가 숫자 필드 3", null),
    
    ADD_NUM_04_T("ADD_NUM_04_T", "추가숫자필드사4", FieldType.NUMBER, false, FieldLevel.HEADER,
        "추가 숫자 필드 4", null),
    
    ADD_NUM_05_T("ADD_NUM_05_T", "추가숫자필드사5", FieldType.NUMBER, false, FieldLevel.HEADER,
        "추가 숫자 필드 5", null),
    
    /**
     * 추가코드필드사1 - STRING(100)
     */
    ADD_CD_01_T("ADD_CD_01_T", "추가코드필드사1", FieldType.TEXT, false, FieldLevel.HEADER,
        "추가 코드 필드 1", null),
    
    /**
     * 추가코드필드사2 - STRING(100)
     */
    ADD_CD_02_T("ADD_CD_02_T", "추가코드필드사2", FieldType.TEXT, false, FieldLevel.HEADER,
        "추가 코드 필드 2", null),
    
    /**
     * 추가코드필드사3 - STRING(100)
     */
    ADD_CD_03_T("ADD_CD_03_T", "추가코드필드사3", FieldType.TEXT, false, FieldLevel.HEADER,
        "추가 코드 필드 3", null),
    
    /**
     * 추가일자필드사1 - STRING(8)
     */
    ADD_DATE_01_T("ADD_DATE_01_T", "추가일자필드사1", FieldType.DATE, false, FieldLevel.HEADER,
        "추가 일자 필드 1", null),
    
    /**
     * 추가일자필드사2 - STRING(8)
     */
    ADD_DATE_02_T("ADD_DATE_02_T", "추가일자필드사2", FieldType.DATE, false, FieldLevel.HEADER,
        "추가 일자 필드 2", null),
    
    /**
     * 추가일자필드사3 - STRING(8)
     */
    ADD_DATE_03_T("ADD_DATE_03_T", "추가일자필드사3", FieldType.DATE, false, FieldLevel.HEADER,
        "추가 일자 필드 3", null),
    
    /**
     * 장문필드1 - STRING(2000)
     */
    U_TXT1("U_TXT1", "장문필드1", FieldType.TEXT, false, FieldLevel.HEADER,
        "긴 텍스트 필드 1", null),
    
    /**
     * 추가장문필드사1 - STRING(2000)
     */
    ADD_LTXT_01_T("ADD_LTXT_01_T", "추가장문필드사1", FieldType.TEXT, false, FieldLevel.HEADER,
        "추가 장문 필드 1", null),
    
    /**
     * 추가장문필드사2 - STRING(2000)
     */
    ADD_LTXT_02_T("ADD_LTXT_02_T", "추가장문필드사2", FieldType.TEXT, false, FieldLevel.HEADER,
        "추가 장문 필드 2", null),
    
    /**
     * 추가장문필드사3 - STRING(2000)
     */
    ADD_LTXT_03_T("ADD_LTXT_03_T", "추가장문필드사3", FieldType.TEXT, false, FieldLevel.HEADER,
        "추가 장문 필드 3", null),
    
    // ========== 라인 필드 (상품 아이템 레벨) ==========
    
    /**
     * 품목코드 (필수) - STRING(20)
     * ERP에 등록된 품목 코드
     */
    PROD_CD("PROD_CD", "품목코드", FieldType.TEXT, true, FieldLevel.LINE,
        "ERP 품목코드 (필수)", null),
    
    /**
     * 품목명 - STRING(100)
     */
    PROD_DES("PROD_DES", "품목명", FieldType.TEXT, false, FieldLevel.LINE,
        "품목 이름", null),
    
    /**
     * 규격 - STRING(100)
     */
    SIZE_DES("SIZE_DES", "규격", FieldType.TEXT, false, FieldLevel.LINE,
        "품목 규격", null),
    
    /**
     * 추가수량 - NUMERIC(28,10)
     * Self-Customizing > 환경설정 > 기능설정 > 재고관리 > 수불단위
     */
    UQTY("UQTY", "추가수량", FieldType.NUMBER, false, FieldLevel.LINE,
        "추가 수량 단위", null),
    
    /**
     * 수량 (필수) - NUMERIC(28,10)
     */
    QTY("QTY", "수량", FieldType.NUMBER, true, FieldLevel.LINE,
        "판매 수량 (필수)", null),
    
    /**
     * 단가 - NUMERIC(28,10)
     */
    PRICE("PRICE", "단가", FieldType.NUMBER, false, FieldLevel.LINE,
        "품목 단가", null),
    
    /**
     * 단가(VAT포함) - NUMERIC(28,10)
     */
    USER_PRICE_VAT("USER_PRICE_VAT", "단가(VAT포함)", FieldType.NUMBER, false, FieldLevel.LINE,
        "VAT 포함 단가", null),
    
    /**
     * 공급가액(판매) - NUMERIC(28,4)
     */
    SUPPLY_AMT("SUPPLY_AMT", "공급가액(판매)", FieldType.NUMBER, false, FieldLevel.LINE,
        "공급가액 (VAT 제외)", null),
    
    /**
     * 공급가액(외화) - NUMERIC(28,4)
     */
    SUPPLY_AMT_F("SUPPLY_AMT_F", "공급가액(외화)", FieldType.NUMBER, false, FieldLevel.LINE,
        "외화 기준 공급가액", null),
    
    /**
     * 부가세 - NUMERIC(28,4)
     */
    VAT_AMT("VAT_AMT", "부가세", FieldType.NUMBER, false, FieldLevel.LINE,
        "부가가치세 금액", null),
    
    /**
     * 적요 - STRING(200)
     */
    REMARKS("REMARKS", "적요", FieldType.TEXT, false, FieldLevel.LINE,
        "라인 적요", null),
    
    /**
     * 관리항목 - STRING(14)
     */
    ITEM_CD("ITEM_CD", "관리항목", FieldType.TEXT, false, FieldLevel.LINE,
        "관리항목 코드", null),
    
    /**
     * 적요1 - STRING(100)
     */
    P_REMARKS1("P_REMARKS1", "적요1", FieldType.TEXT, false, FieldLevel.LINE,
        "라인 적요 1", null),
    
    /**
     * 적요2 - STRING(100)
     */
    P_REMARKS2("P_REMARKS2", "적요2", FieldType.TEXT, false, FieldLevel.LINE,
        "라인 적요 2", null),
    
    /**
     * 적요3 - STRING(100)
     */
    P_REMARKS3("P_REMARKS3", "적요3", FieldType.TEXT, false, FieldLevel.LINE,
        "라인 적요 3", null),
    
    /**
     * 금액1 - NUMERIC(28,10)
     */
    P_AMT1("P_AMT1", "금액1", FieldType.NUMBER, false, FieldLevel.LINE,
        "라인 금액 1", null),
    
    /**
     * 금액2 - NUMERIC(28,10)
     */
    P_AMT2("P_AMT2", "금액2", FieldType.NUMBER, false, FieldLevel.LINE,
        "라인 금액 2", null),
    
    /**
     * 추가장문필드사1~6 - STRING(200)
     */
    ADD_TXT_01("ADD_TXT_01", "추가장문필드사1", FieldType.TEXT, false, FieldLevel.LINE,
        "추가 텍스트 필드 1", null),
    
    ADD_TXT_02("ADD_TXT_02", "추가장문필드사2", FieldType.TEXT, false, FieldLevel.LINE,
        "추가 텍스트 필드 2", null),
    
    ADD_TXT_03("ADD_TXT_03", "추가장문필드사3", FieldType.TEXT, false, FieldLevel.LINE,
        "추가 텍스트 필드 3", null),
    
    ADD_TXT_04("ADD_TXT_04", "추가장문필드사4", FieldType.TEXT, false, FieldLevel.LINE,
        "추가 텍스트 필드 4", null),
    
    ADD_TXT_05("ADD_TXT_05", "추가장문필드사5", FieldType.TEXT, false, FieldLevel.LINE,
        "추가 텍스트 필드 5", null),
    
    ADD_TXT_06("ADD_TXT_06", "추가장문필드사6", FieldType.TEXT, false, FieldLevel.LINE,
        "추가 텍스트 필드 6", null),
    
    /**
     * 추가숫자필드사1~5 - NUMERIC(28,10)
     */
    ADD_NUM_01("ADD_NUM_01", "추가숫자필드사1", FieldType.NUMBER, false, FieldLevel.LINE,
        "추가 숫자 필드 1", null),
    
    ADD_NUM_02("ADD_NUM_02", "추가숫자필드사2", FieldType.NUMBER, false, FieldLevel.LINE,
        "추가 숫자 필드 2", null),
    
    ADD_NUM_03("ADD_NUM_03", "추가숫자필드사3", FieldType.NUMBER, false, FieldLevel.LINE,
        "추가 숫자 필드 3", null),
    
    ADD_NUM_04("ADD_NUM_04", "추가숫자필드사4", FieldType.NUMBER, false, FieldLevel.LINE,
        "추가 숫자 필드 4", null),
    
    ADD_NUM_05("ADD_NUM_05", "추가숫자필드사5", FieldType.NUMBER, false, FieldLevel.LINE,
        "추가 숫자 필드 5", null),
    
    /**
     * 추가코드필드사트1~3 - STRING(100)
     */
    ADD_CD_01("ADD_CD_01", "추가코드필드사트1", FieldType.TEXT, false, FieldLevel.LINE,
        "추가 코드 필드 1", null),
    
    ADD_CD_02("ADD_CD_02", "추가코드필드사트2", FieldType.TEXT, false, FieldLevel.LINE,
        "추가 코드 필드 2", null),
    
    ADD_CD_03("ADD_CD_03", "추가코드필드사트3", FieldType.TEXT, false, FieldLevel.LINE,
        "추가 코드 필드 3", null),
    
    /**
     * 추가코드필드사명1~3 - STRING(100)
     */
    ADD_CD_NM_01("ADD_CD_NM_01", "추가코드필드사명1", FieldType.TEXT, false, FieldLevel.LINE,
        "추가 코드 필드명 1", null),
    
    ADD_CD_NM_02("ADD_CD_NM_02", "추가코드필드사명2", FieldType.TEXT, false, FieldLevel.LINE,
        "추가 코드 필드명 2", null),
    
    ADD_CD_NM_03("ADD_CD_NM_03", "추가코드필드사명3", FieldType.TEXT, false, FieldLevel.LINE,
        "추가 코드 필드명 3", null),
    
    /**
     * 추가일자필드사1~3 - STRING(8)
     */
    ADD_DATE_01("ADD_DATE_01", "추가일자필드사1", FieldType.DATE, false, FieldLevel.LINE,
        "추가 일자 필드 1", null),
    
    ADD_DATE_02("ADD_DATE_02", "추가일자필드사2", FieldType.DATE, false, FieldLevel.LINE,
        "추가 일자 필드 2", null),
    
    ADD_DATE_03("ADD_DATE_03", "추가일자필드사3", FieldType.DATE, false, FieldLevel.LINE,
        "추가 일자 필드 3", null),
    
    /**
     * 출하예정일 - STRING(8)
     */
    REL_DATE("REL_DATE", "출하예정일", FieldType.DATE, false, FieldLevel.LINE,
        "출하예정일자 (YYYYMMDD 형식)", null),
    
    /**
     * 출하번호 - STRING(30)
     */
    REL_NO("REL_NO", "출하번호", FieldType.TEXT, false, FieldLevel.LINE,
        "출하 관리번호", null),
    
    /**
     * 제조지시여부 - STRING(1)
     */
    MAKE_FLAG("MAKE_FLAG", "제조지시여부", FieldType.TEXT, false, FieldLevel.LINE,
        "제조지시 생성 여부 (Y/N)", null),
    
    /**
     * 고객금액 - NUMERIC(28,10)
     */
    CUST_AMT("CUST_AMT", "고객금액", FieldType.NUMBER, false, FieldLevel.LINE,
        "고객이 실제로 지불한 금액", null),
    
    /**
     * 추가코드명1~3 - STRING(100)
     */
    ADD_CDNM_01("ADD_CDNM_01", "추가코드명1", FieldType.TEXT, false, FieldLevel.LINE,
        "추가 코드 명칭 1", null),
    
    ADD_CDNM_02("ADD_CDNM_02", "추가코드명2", FieldType.TEXT, false, FieldLevel.LINE,
        "추가 코드 명칭 2", null),
    
    ADD_CDNM_03("ADD_CDNM_03", "추가코드명3", FieldType.TEXT, false, FieldLevel.LINE,
        "추가 코드 명칭 3", null);
    
    private final String fieldCode;      // API 필드명 (예: "IO_DATE")
    private final String fieldNameKr;    // 한글명 (예: "판매일자")
    private final FieldType fieldType;   // 데이터 타입
    private final boolean required;      // 필수 여부
    private final FieldLevel fieldLevel; // 헤더/라인 구분
    private final String description;    // 설명
    private final String exampleValue;   // 예시 값
    
    /**
     * 필드 코드로 Enum 찾기
     */
    public static ECountField fromCode(String code) {
        for (ECountField field : values()) {
            if (field.fieldCode.equals(code)) {
                return field;
            }
        }
        throw new IllegalArgumentException("Unknown ECount field code: " + code);
    }
    
    /**
     * 필수 필드 목록 조회
     */
    public static ECountField[] getRequiredFields() {
        return java.util.Arrays.stream(values())
            .filter(ECountField::isRequired)
            .toArray(ECountField[]::new);
    }
    
    /**
     * 레벨별 필드 조회
     */
    public static ECountField[] getFieldsByLevel(FieldLevel level) {
        return java.util.Arrays.stream(values())
            .filter(f -> f.fieldLevel == level)
            .toArray(ECountField[]::new);
    }
    
    /**
     * 필드 데이터 타입
     */
    public enum FieldType {
        TEXT,      // 문자열
        NUMBER,    // 숫자
        DATE,      // 날짜 (YYYYMMDD)
        DATETIME,  // 날짜시간
        BOOLEAN    // 논리값
    }
    
    /**
     * 필드 레벨 (전표 헤더 vs 상품 라인)
     */
    public enum FieldLevel {
        HEADER,    // 전표 헤더 레벨 (주문 단위)
        LINE       // 상품 라인 레벨 (주문 아이템 단위)
    }
}
