package com.sellsync.api.domain.shipment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellsync.api.domain.order.entity.Order;
import com.sellsync.api.domain.order.repository.OrderRepository;
import com.sellsync.api.domain.shipment.entity.Shipment;
import com.sellsync.api.domain.shipment.entity.ShipmentUploadHistory;
import com.sellsync.api.domain.shipment.enums.CarrierCode;
import com.sellsync.api.domain.shipment.enums.MarketPushStatus;
import com.sellsync.api.domain.shipment.enums.ShipmentStatus;
import com.sellsync.api.domain.shipment.repository.ShipmentRepository;
import com.sellsync.api.domain.shipment.repository.ShipmentUploadHistoryRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ShipmentExcelService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentUploadHistoryRepository uploadHistoryRepository;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    @Data
    @Builder
    public static class UploadResult {
        private UUID uploadId;
        private int totalRows;
        private int successCount;
        private int failedCount;
        private List<RowError> errors;
    }

    @Data
    @Builder
    public static class RowError {
        private int rowNum;
        private String orderNo;
        private String errorMessage;
    }
    
    @Data
    @Builder
    private static class ShipmentData {
        private Order order;
        private String carrierCode;
        private String carrierName;
        private String trackingNo;
    }

    @Transactional
    public UploadResult processExcel(UUID tenantId, MultipartFile file, UUID userId) {
        log.info("[ShipmentExcel] Processing file: {} for tenant {}", file.getOriginalFilename(), tenantId);

        ShipmentUploadHistory history = ShipmentUploadHistory.builder()
                .tenantId(tenantId)
                .fileName(file.getOriginalFilename())
                .fileSize((int) file.getSize())
                .status("PROCESSING")
                .uploadedBy(userId)
                .build();
        history = uploadHistoryRepository.save(history);

        List<RowError> errors = new ArrayList<>();
        int totalRows = 0;
        int successCount = 0;

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> headerMap = parseHeader(sheet.getRow(0));

            validateHeaders(headerMap);

            // 1. 모든 행의 송장 데이터 빌드
            List<ShipmentData> shipmentDataList = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isEmptyRow(row)) continue;

                totalRows++;

                try {
                    ShipmentData data = buildShipmentData(tenantId, row, headerMap);
                    shipmentDataList.add(data);
                } catch (Exception e) {
                    String orderNo = getCellValue(row, headerMap.get("주문번호"));
                    errors.add(RowError.builder()
                            .rowNum(i + 1)
                            .orderNo(orderNo)
                            .errorMessage(e.getMessage())
                            .build());
                    log.warn("[ShipmentExcel] Row {} validation failed: {}", i + 1, e.getMessage());
                }
            }
            
            // 2. 기존 송장 중복 체크 (벌크 조회)
            List<Shipment> shipmentsToSave = filterDuplicates(tenantId, shipmentDataList);
            
            // 3. 벌크 저장
            if (!shipmentsToSave.isEmpty()) {
                try {
                    shipmentRepository.saveAll(shipmentsToSave);
                    successCount = shipmentsToSave.size();
                    log.info("[ShipmentExcel] Bulk saved {} shipments", successCount);
                } catch (Exception e) {
                    log.error("[ShipmentExcel] Bulk save failed, falling back to individual saves", e);
                    // 폴백: 개별 저장 시도
                    for (Shipment shipment : shipmentsToSave) {
                        try {
                            shipmentRepository.save(shipment);
                            successCount++;
                        } catch (Exception ex) {
                            String orderNo = shipment.getOrderId() != null ? 
                                    shipment.getOrderId().toString() : "unknown";
                            errors.add(RowError.builder()
                                    .orderNo(orderNo)
                                    .errorMessage(ex.getMessage())
                                    .build());
                            log.warn("[ShipmentExcel] Individual save failed for tracking {}: {}", 
                                    shipment.getTrackingNo(), ex.getMessage());
                        }
                    }
                }
            }

            // 이력 업데이트
            history.setStatus("COMPLETED");
            history.setTotalRows(totalRows);
            history.setSuccessCount(successCount);
            history.setFailedCount(errors.size());
            history.setFinishedAt(LocalDateTime.now());
            
            if (!errors.isEmpty()) {
                history.setErrorDetails(objectMapper.writeValueAsString(errors));
            }

        } catch (Exception e) {
            log.error("[ShipmentExcel] Failed to process file", e);
            history.setStatus("FAILED");
            history.setFinishedAt(LocalDateTime.now());
            errors.add(RowError.builder()
                    .rowNum(0)
                    .errorMessage("파일 처리 실패: " + e.getMessage())
                    .build());
        }

        uploadHistoryRepository.save(history);

        return UploadResult.builder()
                .uploadId(history.getUploadId())
                .totalRows(totalRows)
                .successCount(successCount)
                .failedCount(errors.size())
                .errors(errors)
                .build();
    }

    private Map<String, Integer> parseHeader(Row headerRow) {
        Map<String, Integer> headerMap = new HashMap<>();
        
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell != null) {
                String value = cell.getStringCellValue().trim();
                // 다양한 컬럼명 매핑
                if (value.contains("주문") && value.contains("번호")) {
                    headerMap.put("주문번호", i);
                } else if (value.contains("택배") || value.contains("배송사") || value.contains("운송사")) {
                    headerMap.put("택배사", i);
                } else if (value.contains("송장") || value.contains("운송장") || value.contains("tracking")) {
                    headerMap.put("송장번호", i);
                } else if (value.contains("수취인") || value.contains("받는분")) {
                    headerMap.put("수취인", i);
                }
            }
        }
        
        return headerMap;
    }

    private void validateHeaders(Map<String, Integer> headerMap) {
        List<String> required = List.of("주문번호", "택배사", "송장번호");
        List<String> missing = required.stream()
                .filter(h -> !headerMap.containsKey(h))
                .toList();

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("필수 컬럼 누락: " + String.join(", ", missing));
        }
    }

    /**
     * 송장 데이터 빌드 (엔티티 생성 전 검증 및 데이터 준비)
     */
    private ShipmentData buildShipmentData(UUID tenantId, Row row, Map<String, Integer> headerMap) {
        String orderNo = getCellValue(row, headerMap.get("주문번호"));
        String carrier = getCellValue(row, headerMap.get("택배사"));
        String trackingNo = getCellValue(row, headerMap.get("송장번호"));

        if (orderNo == null || orderNo.isBlank()) {
            throw new IllegalArgumentException("주문번호가 없습니다");
        }
        if (trackingNo == null || trackingNo.isBlank()) {
            throw new IllegalArgumentException("송장번호가 없습니다");
        }

        // 택배사 코드 변환
        CarrierCode carrierCode = CarrierCode.resolve(carrier);

        // 주문 찾기 (마켓 주문번호 또는 내부 ID)
        Order order = findOrder(tenantId, orderNo);

        return ShipmentData.builder()
                .order(order)
                .carrierCode(carrierCode.getCode())
                .carrierName(carrierCode.getName())
                .trackingNo(trackingNo.replaceAll("[^0-9]", ""))  // 숫자만 추출
                .build();
    }
    
    /**
     * 중복 송장 필터링 (벌크 조회로 성능 최적화)
     */
    private List<Shipment> filterDuplicates(UUID tenantId, List<ShipmentData> shipmentDataList) {
        if (shipmentDataList.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 1. 기존 송장 벌크 조회 (orderId + carrierCode + trackingNo 조합)
        List<UUID> orderIds = shipmentDataList.stream()
                .map(data -> data.getOrder().getOrderId())
                .distinct()
                .toList();
        
        List<Shipment> existingShipments = shipmentRepository
                .findByTenantIdAndOrderIdIn(tenantId, orderIds);
        
        // 2. 중복 체크를 위한 키 생성 (orderId:carrierCode:trackingNo)
        Set<String> existingKeys = existingShipments.stream()
                .map(s -> String.format("%s:%s:%s", 
                        s.getOrderId(), s.getCarrierCode(), s.getTrackingNo()))
                .collect(Collectors.toSet());
        
        log.debug("[ShipmentExcel] Found {} existing shipments out of {} candidates", 
                existingKeys.size(), shipmentDataList.size());
        
        // 3. 신규 송장만 필터링
        List<Shipment> shipmentsToSave = new ArrayList<>();
        for (ShipmentData data : shipmentDataList) {
            String key = String.format("%s:%s:%s", 
                    data.getOrder().getOrderId(), data.getCarrierCode(), data.getTrackingNo());
            
            if (!existingKeys.contains(key)) {
                Shipment shipment = Shipment.builder()
                        .tenantId(tenantId)
                        .storeId(data.getOrder().getStoreId())
                        .orderId(data.getOrder().getOrderId())
                        .carrierCode(data.getCarrierCode())
                        .carrierName(data.getCarrierName())
                        .trackingNo(data.getTrackingNo())
                        .shipmentStatus(ShipmentStatus.INVOICE_CREATED)
                        .marketPushStatus(MarketPushStatus.PENDING)
                        .build();
                
                shipmentsToSave.add(shipment);
            } else {
                log.debug("[ShipmentExcel] Skipping duplicate shipment: {}", key);
            }
        }
        
        return shipmentsToSave;
    }
    
    /**
     * 개별 행 처리 (레거시 메서드, 하위 호환성 유지)
     * 
     * @deprecated processExcel()에서 벌크 처리를 사용합니다. 개별 호출이 필요한 경우에만 사용하세요.
     */
    @Deprecated
    private void processRow(UUID tenantId, Row row, Map<String, Integer> headerMap) {
        String orderNo = getCellValue(row, headerMap.get("주문번호"));
        String carrier = getCellValue(row, headerMap.get("택배사"));
        String trackingNo = getCellValue(row, headerMap.get("송장번호"));

        if (orderNo == null || orderNo.isBlank()) {
            throw new IllegalArgumentException("주문번호가 없습니다");
        }
        if (trackingNo == null || trackingNo.isBlank()) {
            throw new IllegalArgumentException("송장번호가 없습니다");
        }

        // 택배사 코드 변환
        CarrierCode carrierCode = CarrierCode.resolve(carrier);

        // 주문 찾기 (마켓 주문번호 또는 내부 ID)
        Order order = findOrder(tenantId, orderNo);

        // 중복 체크
        Optional<Shipment> existing = shipmentRepository
                .findByTenantIdAndOrderIdAndCarrierCodeAndTrackingNo(
                        tenantId, order.getOrderId(), carrierCode.getCode(), trackingNo);

        if (existing.isPresent()) {
            log.info("[ShipmentExcel] Shipment already exists for order {}", orderNo);
            return; // 멱등성 - 이미 존재하면 스킵
        }

        // 송장 생성
        Shipment shipment = Shipment.builder()
                .tenantId(tenantId)
                .storeId(order.getStoreId())
                .orderId(order.getOrderId())
                .carrierCode(carrierCode.getCode())
                .carrierName(carrierCode.getName())
                .trackingNo(trackingNo.replaceAll("[^0-9]", ""))  // 숫자만 추출
                .shipmentStatus(ShipmentStatus.INVOICE_CREATED)
                .marketPushStatus(MarketPushStatus.PENDING)
                .build();

        shipmentRepository.save(shipment);
        log.info("[ShipmentExcel] Created shipment for order {} -> {}", orderNo, trackingNo);
    }

    private Order findOrder(UUID tenantId, String orderNo) {
        // 1. 내부 주문 ID로 찾기 (UUID 형식인 경우)
        try {
            UUID orderId = UUID.fromString(orderNo);
            return orderRepository.findById(orderId)
                    .filter(o -> o.getTenantId().equals(tenantId))
                    .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + orderNo));
        } catch (IllegalArgumentException e) {
            // UUID가 아닌 경우 마켓 주문번호로 찾기
        }

        // 2. 마켓 주문번호로 찾기
        return orderRepository.findByTenantIdAndMarketplaceOrderId(tenantId, orderNo)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + orderNo));
    }

    private String getCellValue(Row row, Integer colIndex) {
        if (colIndex == null) return null;
        
        Cell cell = row.getCell(colIndex);
        if (cell == null) return null;

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BLANK -> null;
            default -> cell.toString().trim();
        };
    }

    private boolean isEmptyRow(Row row) {
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellValue(row, i);
                if (value != null && !value.isBlank()) {
                    return false;
                }
            }
        }
        return true;
    }
}
