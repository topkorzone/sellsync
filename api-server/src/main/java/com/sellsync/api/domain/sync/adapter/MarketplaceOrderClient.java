package com.sellsync.api.domain.sync.adapter;

import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.enums.Marketplace;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 마켓플레이스 주문 수집 클라이언트 인터페이스
 * 
 * 목적:
 * - 각 오픈마켓(SmartStore, Coupang 등)의 주문 API를 표준 Order 모델로 변환
 * - 구현체는 마켓별 API 호출 및 데이터 매핑 담당
 * 
 * 구현 클래스:
 * - NaverSmartStoreOrderClient
 * - CoupangOrderClient
 */
public interface MarketplaceOrderClient {

    /**
     * 지원하는 마켓플레이스 반환
     */
    Marketplace getMarketplace();

    /**
     * 주문 목록 수집 (기간 기준)
     * 
     * @param storeCredentials 상점 인증 정보 (JSON)
     * @param startTime 수집 시작 시각
     * @param endTime 수집 종료 시각
     * @return 표준 Order 모델 리스트
     * @throws MarketplaceApiException 마켓 API 오류
     */
    List<Order> fetchOrders(String storeCredentials, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 단일 주문 조회
     * 
     * @param storeCredentials 상점 인증 정보 (JSON)
     * @param marketplaceOrderId 마켓 주문번호
     * @return 표준 Order 모델
     * @throws MarketplaceApiException 마켓 API 오류
     */
    Order fetchOrder(String storeCredentials, String marketplaceOrderId);

    /**
     * API 인증 테스트
     * 
     * @param storeCredentials 상점 인증 정보 (JSON)
     * @return 인증 성공 여부
     */
    boolean testConnection(String storeCredentials);

    /**
     * API Rate Limit 확인
     * 
     * @return 남은 요청 횟수 (null이면 무제한)
     */
    default Integer getRemainingQuota() {
        return null;
    }
}
