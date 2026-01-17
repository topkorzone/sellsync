package com.sellsync.api.domain.settlement.adapter;

import com.sellsync.api.domain.settlement.dto.MarketplaceSettlementData;

import java.time.LocalDate;
import java.util.List;

/**
 * 마켓플레이스 정산 데이터 수집 클라이언트 인터페이스
 * 
 * 역할:
 * - 오픈마켓 정산 API 연동
 * - 정산 데이터 수집
 * - 정산 상세 정보 조회
 */
public interface MarketplaceSettlementClient {

    /**
     * 마켓플레이스 코드 반환
     */
    String getMarketplaceCode();

    /**
     * 정산 데이터 수집
     * 
     * @param startDate 정산 시작일
     * @param endDate 정산 종료일
     * @param credentials 인증 정보 (JSON)
     * @return 정산 데이터 목록
     */
    List<MarketplaceSettlementData> fetchSettlements(
        LocalDate startDate,
        LocalDate endDate,
        String credentials
    );

    /**
     * 정산 상세 정보 조회
     * 
     * @param settlementId 정산 ID
     * @param credentials 인증 정보
     * @return 정산 상세 데이터
     */
    MarketplaceSettlementData fetchSettlement(String settlementId, String credentials);

    /**
     * 연결 테스트
     * 
     * @param credentials 인증 정보
     * @return 연결 성공 여부
     */
    boolean testConnection(String credentials);

    /**
     * API 호출 제한 확인
     * 
     * @return 남은 호출 횟수
     */
    Integer getRemainingQuota();
}
