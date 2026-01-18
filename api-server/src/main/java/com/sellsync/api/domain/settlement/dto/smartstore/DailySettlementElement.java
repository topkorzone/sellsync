package com.sellsync.api.domain.settlement.dto.smartstore;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * SmartStore 정산 개별 내역 DTO
 * 
 * <p>API: GET /v1/pay-settle/settle/daily 또는 /v1/pay-settle/settle/case
 * <p>각 정산 건에 대한 상세 정보를 포함
 * <p>여러 API 응답 형식을 지원하기 위해 유연하게 설계됨
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)  // 알 수 없는 필드 무시
public class DailySettlementElement {

    private static final DateTimeFormatter DATE_FORMATTER_YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_FORMATTER_HYPHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ===== Daily API 필드 =====
    
    /**
     * 정산기준 시작일 (yyyyMMdd 형식) - Daily API용
     * 예: "20260115"
     */
    @JsonProperty("settleBasisStartDate")
    private String settleBasisStartDate;

    /**
     * 정산기준 종료일 (yyyyMMdd 형식) - Daily API용
     * 예: "20260115"
     */
    @JsonProperty("settleBasisEndDate")
    private String settleBasisEndDate;

    // ===== Case API 필드 (실제 필드명) =====
    
    /**
     * 정산기준일 (yyyy-MM-dd 형식) - Case API용
     * 예: "2026-01-14"
     * 정산이 완료된 경우에만 존재
     */
    @JsonProperty("settleBasisDate")
    private String settleBasisDate;
    
    /**
     * 결제일 (yyyy-MM-dd 형식)
     * 예: "2026-01-11"
     * ✅ 항상 존재하는 필드
     */
    @JsonProperty("payDate")
    private String payDate;
    
    /**
     * 주문번호
     */
    @JsonProperty("orderId")
    private String orderId;
    
    /**
     * 상품주문번호
     */
    @JsonProperty("productOrderId")
    private String productOrderId;
    
    /**
     * 상품주문 유형
     * 예: PROD_ORDER (일반 상품주문)
     */
    @JsonProperty("productOrderType")
    private String productOrderType;
    
    /**
     * 정산 타입
     * - NORMAL_SETTLE_ORIGINAL: 정상 정산 완료
     * - NORMAL_SETTLE_BEFORE_CANCEL: 정산 전 (미완료)
     */
    @JsonProperty("settleType")
    private String settleType;
    
    // ===== 상품 정보 =====
    
    /**
     * 상품 ID
     */
    @JsonProperty("productId")
    private String productId;
    
    /**
     * 상품명
     */
    @JsonProperty("productName")
    private String productName;
    
    /**
     * 구매자명
     */
    @JsonProperty("purchaserName")
    private String purchaserName;
    
    // ===== 금액 필드 (상세 정산 정보) =====
    
    /**
     * 결제 정산 금액 (원)
     * 실제 결제 금액
     */
    @JsonProperty("paySettleAmount")
    private Long paySettleAmount;
    
    /**
     * 총 결제 수수료 (원)
     * 음수값으로 내려옴
     */
    @JsonProperty("totalPayCommissionAmount")
    private Long totalPayCommissionAmount;
    
    /**
     * 무이자 할부 수수료 (원)
     */
    @JsonProperty("freeInstallmentCommissionAmount")
    private Long freeInstallmentCommissionAmount;
    
    /**
     * 판매 연동 수수료 (원)
     * 음수값으로 내려옴
     */
    @JsonProperty("sellingInterlockCommissionAmount")
    private Long sellingInterlockCommissionAmount;
    
    /**
     * 정산 예정 금액 (원)
     * 수수료가 차감된 실제 정산될 금액
     */
    @JsonProperty("settleExpectAmount")
    private Long settleExpectAmount;
    
    // ===== 판매자 정보 =====
    
    /**
     * 판매자 ID
     * 예: ncp_1ot94p_01
     */
    @JsonProperty("merchantId")
    private String merchantId;
    
    /**
     * 판매자명
     */
    @JsonProperty("merchantName")
    private String merchantName;
    
    /**
     * 계약 번호
     */
    @JsonProperty("contractNo")
    private String contractNo;
    
    // 기존 Case API 필드들 (사용 안 됨, 호환성 유지)
    @JsonProperty("paymentYmdt")
    private String paymentYmdt;
    @JsonProperty("useCfmYmdt")
    private String useCfmYmdt;
    @JsonProperty("tradeDate")
    private String tradeDate;
    @JsonProperty("tradeConfirmYmdt")
    private String tradeConfirmYmdt;

    // ===== 공통 필드 =====
    
    /**
     * 정산 예정일 (yyyyMMdd 형식)
     * 예: "20260117"
     */
    @JsonProperty("settleExpectDate")
    private String settleExpectDate;

    /**
     * 정산 완료일 (yyyyMMdd 형식, nullable)
     * 예: "20260117" 또는 null
     */
    @JsonProperty("settleCompleteDate")
    private String settleCompleteDate;

    /**
     * 정산 금액 (원)
     */
    @JsonProperty("settleAmount")
    private Long settleAmount;

    /**
     * 수수료 (원)
     */
    @JsonProperty("commissionAmount")
    private Long commissionAmount;

    /**
     * 배송비 정산 금액 (원)
     */
    @JsonProperty("shippingSettleAmount")
    private Long shippingSettleAmount;

    /**
     * 혜택 정산 금액 (원)
     */
    @JsonProperty("benefitSettleAmount")
    private Long benefitSettleAmount;

    /**
     * 상품주문 건수
     */
    @JsonProperty("productOrderCount")
    private Integer productOrderCount;

    // ===== 헬퍼 메서드 =====

    /**
     * 정산기준 시작일을 LocalDate로 변환
     * 여러 필드를 순차적으로 시도하여 첫 번째 유효한 날짜를 반환
     * 
     * @return LocalDate 객체, 파싱 실패 시 null
     */
    @JsonIgnore
    public LocalDate getSettleBasisStartDateAsLocalDate() {
        // 1. settleBasisStartDate 시도 (Daily API)
        LocalDate date = parseDate(settleBasisStartDate);
        if (date != null) {
            log.debug("[DTO] settleBasisStartDate 파싱 성공: {}", date);
            return date;
        }
        
        // 2. settleBasisDate 시도 (Case API) ✅
        date = parseDate(settleBasisDate);
        if (date != null) {
            log.debug("[DTO] settleBasisDate 파싱 성공: {}", date);
            return date;
        }
        
        // 3. payDate 시도 (Case API - 항상 있음) ✅
        date = parseDate(payDate);
        if (date != null) {
            log.debug("[DTO] payDate 파싱 성공: {}", date);
            return date;
        }
        
        // 4. 기존 필드들 (호환성)
        date = parseDateFromDateTime(paymentYmdt);
        if (date != null) {
            log.debug("[DTO] paymentYmdt에서 날짜 추출 성공: {}", date);
            return date;
        }
        
        date = parseDate(tradeDate);
        if (date != null) {
            log.debug("[DTO] tradeDate 파싱 성공: {}", date);
            return date;
        }
        
        log.warn("[DTO] 모든 날짜 필드 파싱 실패 - settleBasisDate: {}, payDate: {}, settleBasisStartDate: {}", 
                settleBasisDate, payDate, settleBasisStartDate);
        return null;
    }

    /**
     * 정산기준 종료일을 LocalDate로 변환
     * 
     * @return LocalDate 객체, 파싱 실패 시 null
     */
    @JsonIgnore
    public LocalDate getSettleBasisEndDateAsLocalDate() {
        // 1. settleBasisEndDate 시도
        LocalDate date = parseDate(settleBasisEndDate);
        if (date != null) return date;
        
        // 2. Case API의 경우 건별이므로 시작일과 동일
        return getSettleBasisStartDateAsLocalDate();
    }

    /**
     * 정산 예정일을 LocalDate로 변환
     * 
     * @return LocalDate 객체, 파싱 실패 시 null
     */
    @JsonIgnore
    public LocalDate getSettleExpectDateAsLocalDate() {
        return parseDate(settleExpectDate);
    }

    /**
     * 정산 완료일을 LocalDate로 변환
     * 
     * @return LocalDate 객체, 파싱 실패 또는 null 값인 경우 null
     */
    @JsonIgnore
    public LocalDate getSettleCompleteDateAsLocalDate() {
        return parseDate(settleCompleteDate);
    }

    /**
     * 날짜 문자열을 LocalDate로 파싱 (여러 포맷 시도)
     * 
     * @param dateString yyyyMMdd 또는 yyyy-MM-dd 형식의 날짜 문자열
     * @return LocalDate 객체, 파싱 실패 또는 null인 경우 null
     */
    private LocalDate parseDate(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
        }
        
        try {
            // yyyyMMdd 포맷 시도 (8자리)
            if (dateString.length() == 8 && dateString.matches("\\d{8}")) {
                return LocalDate.parse(dateString, DATE_FORMATTER_YYYYMMDD);
            }
            // yyyy-MM-dd 포맷 시도 (10자리, 하이픈 포함)
            else if (dateString.length() == 10 && dateString.contains("-")) {
                return LocalDate.parse(dateString, DATE_FORMATTER_HYPHEN);
            }
        } catch (Exception e) {
            log.debug("[DTO] 날짜 파싱 실패: {}", dateString, e);
        }
        
        return null;
    }

    /**
     * 날짜시간 문자열에서 날짜만 추출
     * yyyyMMddHHmmss -> LocalDate
     * 
     * @param dateTimeString yyyyMMddHHmmss 형식의 날짜시간 문자열
     * @return LocalDate 객체, 파싱 실패 또는 null인 경우 null
     */
    private LocalDate parseDateFromDateTime(String dateTimeString) {
        if (dateTimeString == null || dateTimeString.isEmpty()) {
            return null;
        }
        
        try {
            // yyyyMMddHHmmss 포맷 (14자리)
            if (dateTimeString.length() >= 8) {
                String dateOnly = dateTimeString.substring(0, 8);
                return LocalDate.parse(dateOnly, DATE_FORMATTER_YYYYMMDD);
            }
        } catch (Exception e) {
            log.debug("[DTO] 날짜시간 파싱 실패: {}", dateTimeString, e);
        }
        
        return null;
    }

    /**
     * 실제 정산된 순 금액 계산
     * 정산금액 - 수수료 + 배송비 정산 금액 + 혜택 정산 금액
     * 
     * @return 순 정산 금액
     */
    @JsonIgnore
    public Long getNetSettlementAmount() {
        long settleAmountValue = (settleAmount != null) ? settleAmount : 0L;
        long commissionValue = (commissionAmount != null) ? commissionAmount : 0L;
        long shippingValue = (shippingSettleAmount != null) ? shippingSettleAmount : 0L;
        long benefitValue = (benefitSettleAmount != null) ? benefitSettleAmount : 0L;
        
        return settleAmountValue - commissionValue + shippingValue + benefitValue;
    }

    /**
     * 총 수수료 계산 (절대값으로 변환)
     * API 응답에서 수수료는 음수로 내려오므로 절대값으로 변환하여 합산
     * 
     * @return 총 수수료 금액 (양수)
     */
    @JsonIgnore
    public Long getTotalCommission() {
        long total = 0L;
        if (totalPayCommissionAmount != null) {
            total += Math.abs(totalPayCommissionAmount);
        }
        if (sellingInterlockCommissionAmount != null) {
            total += Math.abs(sellingInterlockCommissionAmount);
        }
        if (freeInstallmentCommissionAmount != null) {
            total += Math.abs(freeInstallmentCommissionAmount);
        }
        return total;
    }

    /**
     * 정산 금액 계산
     * settleExpectAmount가 있으면 우선 사용하고, 없으면 paySettleAmount에서 수수료 차감
     * 
     * @return 계산된 정산 금액
     */
    @JsonIgnore
    public Long getCalculatedSettleAmount() {
        if (settleExpectAmount != null) {
            return settleExpectAmount;
        }
        // fallback: paySettleAmount에서 수수료 차감
        long pay = paySettleAmount != null ? paySettleAmount : 0L;
        return pay - getTotalCommission();
    }

    /**
     * 유효한 정산 금액 반환
     * 기존 settleAmount 필드가 있으면 우선 사용하고, 없으면 계산된 값 반환
     * 
     * @return 유효한 정산 금액
     */
    @JsonIgnore
    public Long getEffectiveSettleAmount() {
        if (settleAmount != null) {
            return settleAmount;
        }
        return getCalculatedSettleAmount();
    }

    /**
     * 유효한 수수료 금액 반환
     * 기존 commissionAmount 필드가 있으면 우선 사용하고, 없으면 계산된 값 반환
     * 
     * @return 유효한 수수료 금액
     */
    @JsonIgnore
    public Long getEffectiveCommissionAmount() {
        if (commissionAmount != null) {
            return commissionAmount;
        }
        return getTotalCommission();
    }

    /**
     * 정산 완료 여부
     * 
     * @return 정산 완료일이 있거나 settleType이 정산 완료 상태이면 true
     */
    @JsonIgnore
    public boolean isSettlementCompleted() {
        // 1. settleCompleteDate 확인
        if (settleCompleteDate != null && !settleCompleteDate.isEmpty()) {
            return true;
        }
        
        // 2. settleType 확인 (Case API)
        if (settleType != null) {
            // NORMAL_SETTLE_ORIGINAL: 정상 정산 완료
            // NORMAL_SETTLE_BEFORE_CANCEL: 정산 전 (미완료)
            return settleType.contains("ORIGINAL") || settleType.contains("COMPLETE");
        }
        
        return false;
    }
    
    /**
     * 정산 대기 중 여부
     * 
     * @return 정산이 아직 완료되지 않은 상태이면 true
     */
    @JsonIgnore
    public boolean isSettlementPending() {
        return !isSettlementCompleted();
    }
}
