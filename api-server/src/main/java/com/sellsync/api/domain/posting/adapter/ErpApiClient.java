package com.sellsync.api.domain.posting.adapter;

import com.sellsync.api.domain.posting.entity.Posting;

/**
 * ERP API 클라이언트 인터페이스
 * 
 * 목적:
 * - 각 ERP(이카운트, SAP 등)의 전표 전송 API를 표준화
 * - 구현체는 ERP별 API 호출 및 응답 처리 담당
 * 
 * 구현 클래스:
 * - EcountApiClient (이카운트)
 * - SapApiClient (SAP)
 */
public interface ErpApiClient {

    /**
     * 지원하는 ERP 코드 반환
     */
    String getErpCode();

    /**
     * 전표 전송
     * 
     * @param posting 전표 정보
     * @param credentials ERP 인증 정보 (JSON)
     * @return ERP 전표 번호
     * @throws ErpApiException ERP API 오류
     */
    String postDocument(Posting posting, String credentials);

    /**
     * 전표 조회
     * 
     * @param erpDocumentNo ERP 전표 번호
     * @param credentials ERP 인증 정보 (JSON)
     * @return 전표 상태 정보 (JSON)
     * @throws ErpApiException ERP API 오류
     */
    String getDocument(String erpDocumentNo, String credentials);

    /**
     * API 인증 테스트
     * 
     * @param credentials ERP 인증 정보 (JSON)
     * @return 인증 성공 여부
     */
    boolean testConnection(String credentials);

    /**
     * API Rate Limit 확인
     * 
     * @return 남은 요청 횟수 (null이면 무제한)
     */
    default Integer getRemainingQuota() {
        return null;
    }
}
