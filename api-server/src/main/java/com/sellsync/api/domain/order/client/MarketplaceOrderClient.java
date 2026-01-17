package com.sellsync.api.domain.order.client;

import com.sellsync.api.domain.order.dto.MarketplaceOrderDto;
import com.sellsync.api.domain.order.enums.Marketplace;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 마켓플레이스 주문 수집 클라이언트 인터페이스
 */
public interface MarketplaceOrderClient {

    /**
     * 지원하는 마켓플레이스 반환
     */
    Marketplace getMarketplace();

    /**
     * 주문 목록 수집
     * 
     * @param credentials 인증 정보 (JSON 문자열)
     * @param from 시작 일시
     * @param to 종료 일시
     * @return 통합 주문 DTO 리스트
     */
    List<MarketplaceOrderDto> fetchOrders(
            String credentials,
            LocalDateTime from,
            LocalDateTime to
    );
}
