package com.sellsync.api.domain.shipping.client;

import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.shipping.dto.ShipmentPushRequest;
import com.sellsync.api.domain.shipping.dto.ShipmentPushResult;

/**
 * 마켓플레이스 송장 반영 클라이언트 인터페이스
 * 
 * 목적:
 * - 각 오픈마켓(SmartStore, Coupang 등)에 송장번호를 반영
 * - 구현체는 마켓별 송장 API 호출 및 응답 처리 담당
 * 
 * 구현 클래스:
 * - SmartStoreShipmentClient
 * - CoupangShipmentClient
 */
public interface MarketShipmentClient {

    /**
     * 지원하는 마켓플레이스 반환
     */
    Marketplace getMarketplace();

    /**
     * 송장번호를 마켓에 반영
     * 
     * @param credentials 상점 인증 정보 (JSON)
     * @param request 송장 반영 요청 데이터
     * @return 송장 반영 결과
     */
    ShipmentPushResult pushShipment(String credentials, ShipmentPushRequest request);

    /**
     * API 인증 테스트
     * 
     * @param credentials 상점 인증 정보 (JSON)
     * @return 인증 성공 여부
     */
    default boolean testConnection(String credentials) {
        return true;
    }
}
