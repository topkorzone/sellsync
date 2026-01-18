package com.sellsync.api.domain.posting.service;

import com.sellsync.api.domain.erp.dto.ecount.EcountBulkData;
import com.sellsync.api.domain.erp.dto.ecount.EcountSaleItem;
import com.sellsync.api.domain.erp.dto.ecount.EcountSaleRequest;
import com.sellsync.api.domain.mapping.entity.ProductMapping;
import com.sellsync.api.domain.mapping.repository.ProductMappingRepository;
import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.entity.OrderItem;
import com.sellsync.api.domain.order.enums.ItemStatus;
import com.sellsync.api.domain.order.enums.Marketplace;
import com.sellsync.api.domain.settlement.entity.SettlementOrder;
import com.sellsync.api.domain.store.entity.Store;
import com.sellsync.api.util.VatCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 이카운트 전표 Builder 서비스
 * 
 * 주문 기반으로 4가지 전표 라인을 생성합니다:
 * 1. 상품 매출 라인
 * 2. 상품판매 수수료 라인
 * 3. 배송비 라인 (배송비 > 0인 경우만)
 * 4. 배송비 수수료 라인 (배송비 > 0인 경우만)
 * 
 * 중요: UPLOAD_SER_NO가 동일해야 하나의 전표로 묶임
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EcountPostingBuilder {
    
    private final VatCalculator vatCalculator;
    private final ProductMappingRepository productMappingRepository;
    
    /**
     * 주문 기반 4가지 전표 라인 생성
     * 중요: UPLOAD_SER_NO가 동일해야 하나의 전표로 묶임
     */
    public EcountSaleRequest buildSaleRequest(Order order, Store store, SettlementOrder settlement) {
        List<EcountSaleItem> items = new ArrayList<>();
        
        // 동일한 UPLOAD_SER_NO 사용 (하나의 전표로 묶기 위함)
        int uploadSerNo = generateUploadSerNo(order);
        
        String ioDate = formatDate(order.getOrderedAt());
        String remarks = buildRemarks(order);
        String whCd = store.getDefaultWarehouseCode();
        String cust = store.getDefaultCustomerCode();
        
        log.debug("[EcountPostingBuilder] 전표 생성 시작: orderId={}, uploadSerNo={}, ioDate={}", 
                order.getOrderId(), uploadSerNo, ioDate);
        
        // 1. 상품 매출 라인 (항상 생성)
        for (OrderItem orderItem : order.getItems()) {
            if (orderItem.getItemStatus() == ItemStatus.NORMAL) {
                try {
                    EcountSaleItem item = buildProductSalesItem(orderItem, order.getTenantId(), order.getStoreId(),
                            uploadSerNo, ioDate, remarks, whCd, cust);
                    items.add(item);
                } catch (Exception e) {
                    log.error("[EcountPostingBuilder] 상품 라인 생성 실패: orderItemId={}, error={}", 
                            orderItem.getOrderItemId(), e.getMessage(), e);
                    throw new RuntimeException("상품 라인 생성 실패: " + orderItem.getProductName(), e);
                }
            }
        }
        
        // 2. 상품판매 수수료 라인 (항상 생성)
        if (settlement != null) {
            try {
                items.add(buildProductCommissionItem(
                    order, store, settlement, uploadSerNo, ioDate, remarks, whCd, cust
                ));
            } catch (Exception e) {
                log.error("[EcountPostingBuilder] 상품 수수료 라인 생성 실패: orderId={}, error={}", 
                        order.getOrderId(), e.getMessage(), e);
                throw new RuntimeException("상품 수수료 라인 생성 실패", e);
            }
        }
        
        // 3. 배송비 관련 라인 (배송비 > 0 인 경우만)
        if (hasShippingFee(order)) {
            try {
                // 배송비 라인
                items.add(buildShippingFeeItem(order, store, uploadSerNo, ioDate, remarks, whCd, cust));
                
                // 배송비 수수료 라인
                if (settlement != null) {
                    items.add(buildShippingCommissionItem(
                        order, store, settlement, uploadSerNo, ioDate, remarks, whCd, cust
                    ));
                }
            } catch (Exception e) {
                log.error("[EcountPostingBuilder] 배송비 라인 생성 실패: orderId={}, error={}", 
                        order.getOrderId(), e.getMessage(), e);
                throw new RuntimeException("배송비 라인 생성 실패", e);
            }
        }
        
        log.info("[EcountPostingBuilder] 전표 생성 완료: orderId={}, 라인수={}", order.getOrderId(), items.size());
        return EcountSaleRequest.of(items);
    }
    
    /**
     * UPLOAD_SER_NO 생성 (주문 단위로 유니크)
     * 주문 시간 기반으로 생성 (초 단위)
     * 
     * 타입: SMALLINT(4,0) - 허용 범위 0~9999 (최대 4자리)
     */
    private int generateUploadSerNo(Order order) {
        // 주문일시를 기반으로 유니크한 번호 생성 (4자리로 제한)
        long timestamp = order.getOrderedAt().toEpochSecond(java.time.ZoneOffset.UTC);
        return (int) (timestamp % 10000);  // 0~9999 범위 (4자리)
    }
    
    /**
     * 상품 매출 전표 Item 생성
     */
    private EcountSaleItem buildProductSalesItem(
            OrderItem orderItem, UUID tenantId, UUID storeId, int serNo, String ioDate, 
            String remarks, String whCd, String cust) {
        
        // ERP 품목코드 조회
        String erpItemCode = getErpItemCode(tenantId, storeId, orderItem);
        if (erpItemCode == null) {
            throw new RuntimeException("ERP 품목코드가 매핑되지 않음: " + orderItem.getProductName());
        }
        
        int totalPrice = orderItem.getLineAmount().intValue();
        VatCalculator.VatBreakdown vat = vatCalculator.breakdown(totalPrice);
        
        String productDescription = orderItem.getProductName();
        if (orderItem.getOptionName() != null && !orderItem.getOptionName().isEmpty()) {
            productDescription += " - " + orderItem.getOptionName();
        }
        
        EcountBulkData data = EcountBulkData.builder()
            .uploadSerNo(serNo)
            .prodCd(erpItemCode)  // 매핑된 ERP 품목코드
            .prodDes(productDescription)
            .qty(orderItem.getQuantity())
            .unit("")
            .userPriceVat(totalPrice)
            .supplyAmt(vat.getSupplyAmount())
            .vatAmt(vat.getVatAmount())
            .pAmt1(totalPrice)
            .remarks(remarks)
            .pRemarks1(remarks)
            .ioDate(ioDate)
            .whCd(whCd)
            .cust(cust)
            .build();
        
        return EcountSaleItem.of(data);
    }
    
    /**
     * 배송비 전표 Item 생성
     */
    private EcountSaleItem buildShippingFeeItem(
            Order order, Store store, int serNo, String ioDate,
            String remarks, String whCd, String cust) {
        
        String shippingItemCode = store.getShippingItemCode();
        if (shippingItemCode == null || shippingItemCode.isEmpty()) {
            throw new RuntimeException("스토어에 배송비 품목코드가 설정되지 않음: " + store.getStoreName());
        }
        
        int shippingFee = order.getShippingFee().intValue();
        VatCalculator.VatBreakdown vat = vatCalculator.breakdown(shippingFee);
        
        EcountBulkData data = EcountBulkData.builder()
            .uploadSerNo(serNo)
            .prodCd(shippingItemCode)  // 스토어에 설정된 배송비 품목코드
            .prodDes("택배비 " + shippingFee + "원")
            .qty(1)
            .unit("")
            .userPriceVat(shippingFee)
            .supplyAmt(vat.getSupplyAmount())
            .vatAmt(vat.getVatAmount())
            .pAmt1(shippingFee)
            .remarks(order.getReceiverName())
            .pRemarks1(order.getReceiverName())
            .ioDate(ioDate)
            .whCd(whCd)
            .cust(cust)
            .build();
        
        return EcountSaleItem.of(data);
    }
    
    /**
     * 상품판매 수수료 전표 Item 생성
     */
    private EcountSaleItem buildProductCommissionItem(
            Order order, Store store, SettlementOrder settlement,
            int serNo, String ioDate, String remarks, String whCd, String cust) {
        
        String commissionItemCode = store.getCommissionItemCode();
        if (commissionItemCode == null || commissionItemCode.isEmpty()) {
            throw new RuntimeException("스토어에 상품 수수료 품목코드가 설정되지 않음: " + store.getStoreName());
        }
        
        int commissionAmount = settlement.getCommissionAmount().intValue();
        VatCalculator.VatBreakdown vat = vatCalculator.breakdown(commissionAmount);
        
        String marketName = getMarketDisplayName(store.getMarketplace());
        
        EcountBulkData data = EcountBulkData.builder()
            .uploadSerNo(serNo)
            .prodCd(commissionItemCode)  // 스토어별 수수료 품목코드
            .prodDes(marketName + " 수수료")
            .qty(1)
            .unit("")
            .userPriceVat(commissionAmount)
            .supplyAmt(vat.getSupplyAmount())
            .vatAmt(vat.getVatAmount())
            .pAmt1(commissionAmount)
            .remarks(order.getReceiverName())
            .pRemarks1(order.getReceiverName())
            .ioDate(ioDate)
            .whCd(whCd)
            .cust(cust)
            .build();
        
        return EcountSaleItem.of(data);
    }
    
    /**
     * 배송비 수수료 전표 Item 생성
     */
    private EcountSaleItem buildShippingCommissionItem(
            Order order, Store store, SettlementOrder settlement,
            int serNo, String ioDate, String remarks, String whCd, String cust) {
        
        String shippingCommissionItemCode = store.getShippingCommissionItemCode();
        if (shippingCommissionItemCode == null || shippingCommissionItemCode.isEmpty()) {
            throw new RuntimeException("스토어에 배송비 수수료 품목코드가 설정되지 않음: " + store.getStoreName());
        }
        
        // 배송비 수수료 = 배송비 * 수수료율 (정산 데이터에서 가져오거나 계산)
        int shippingCommission = calculateShippingCommission(order, settlement);
        VatCalculator.VatBreakdown vat = vatCalculator.breakdown(shippingCommission);
        
        EcountBulkData data = EcountBulkData.builder()
            .uploadSerNo(serNo)
            .prodCd(shippingCommissionItemCode)  // 배송비 수수료 품목코드
            .prodDes("배송비 수수료")
            .qty(1)
            .unit("")
            .userPriceVat(shippingCommission)
            .supplyAmt(vat.getSupplyAmount())
            .vatAmt(vat.getVatAmount())
            .pAmt1(shippingCommission)
            .remarks(order.getReceiverName())
            .pRemarks1(order.getReceiverName())
            .ioDate(ioDate)
            .whCd(whCd)
            .cust(cust)
            .build();
        
        return EcountSaleItem.of(data);
    }
    
    // ========== Helper Methods ==========
    
    /**
     * ERP 품목코드 조회
     */
    private String getErpItemCode(UUID tenantId, UUID storeId, OrderItem orderItem) {
        Optional<ProductMapping> mapping = productMappingRepository.findMapping(
            tenantId, storeId,
            orderItem.getMarketplaceProductId(),
            orderItem.getMarketplaceSku()
        );
        
        if (mapping.isEmpty() || !mapping.get().isMapped()) {
            log.warn("[EcountPostingBuilder] ERP 품목코드 매핑 없음: tenantId={}, storeId={}, productId={}, sku={}", 
                    tenantId, storeId, orderItem.getMarketplaceProductId(), orderItem.getMarketplaceSku());
            return null;
        }
        
        return mapping.get().getErpItemCode();
    }
    
    /**
     * 배송비가 있는지 확인
     */
    private boolean hasShippingFee(Order order) {
        return order.getShippingFee() != null 
            && order.getShippingFee() > 0;
    }
    
    /**
     * 날짜 포맷팅
     */
    private String formatDate(LocalDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }
    
    /**
     * 비고 생성
     */
    private String buildRemarks(Order order) {
        return String.format("주문번호: %s %s", 
            order.getMarketplaceOrderId(),
            order.getReceiverName()
        );
    }
    
    /**
     * 마켓 표시명 가져오기
     */
    private String getMarketDisplayName(Marketplace marketplace) {
        return switch (marketplace) {
            case NAVER_SMARTSTORE -> "스마트스토어";
            case COUPANG -> "쿠팡";
            default -> marketplace.name();
        };
    }
    
    /**
     * 배송비 수수료 계산
     */
    private int calculateShippingCommission(Order order, SettlementOrder settlement) {
        // 정산 데이터에 배송비 수수료가 있으면 사용
        if (settlement != null 
                && settlement.getShippingFeeSettled() != null
                && settlement.getShippingFeeCharged() != null) {
            
            // 배송비 차액 = 마켓 정산 배송비 - 고객 결제 배송비
            // 만약 차액이 음수면 그것이 수수료
            BigDecimal diff = settlement.getShippingFeeCharged()
                    .subtract(settlement.getShippingFeeSettled());
            
            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                return diff.intValue();
            }
        }
        
        // 정산 데이터가 없으면 배송비의 기본 수수료율로 계산 (마켓별로 다를 수 있음)
        Long shippingFee = order.getShippingFee();
        if (shippingFee == null || shippingFee == 0) {
            return 0;
        }
        
        BigDecimal rate = new BigDecimal("0.055"); // 기본 5.5%
        return BigDecimal.valueOf(shippingFee)
                .multiply(rate)
                .setScale(0, RoundingMode.FLOOR)
                .intValue();
    }
}
