package com.sellsync.api.domain.shipping.service;

/**
 * SmartStore 송장 업데이트 클라이언트 인터페이스
 * (T-001-3: 마켓 송장 푸시)
 */
public interface SmartStoreShipmentClient {

    /**
     * SmartStore에 송장번호 업데이트 요청
     * 
     * @param orderId 마켓 주문번호 (SmartStore 주문번호)
     * @param carrierCode 택배사 코드 (CJ, HANJIN 등)
     * @param trackingNo 송장번호
     * @return API 응답 (JSON 문자열)
     * @throws Exception 네트워크 오류, API 오류 등
     */
    String updateTracking(String orderId, String carrierCode, String trackingNo) throws Exception;
}
