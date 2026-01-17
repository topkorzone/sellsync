package com.sellsync.api.domain.erp.client;

import com.sellsync.api.domain.erp.dto.*;

import java.util.List;
import java.util.UUID;

public interface ErpClient {

    String getErpCode();

    /**
     * 매출 전표 등록
     */
    ErpPostingResult postSalesDocument(UUID tenantId, ErpSalesDocumentRequest request);

    /**
     * 품목 목록 조회
     */
    List<ErpItemDto> getItems(UUID tenantId, ErpItemSearchRequest request);

    /**
     * 거래처 목록 조회
     */
    List<ErpCustomerDto> getCustomers(UUID tenantId, ErpCustomerSearchRequest request);

    /**
     * 연결 테스트
     */
    boolean testConnection(UUID tenantId);
}
