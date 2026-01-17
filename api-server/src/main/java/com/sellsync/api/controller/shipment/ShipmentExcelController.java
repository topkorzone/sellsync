package com.sellsync.api.controller.shipment;

import com.sellsync.api.common.ApiResponse;
import com.sellsync.api.domain.shipment.entity.ShipmentUploadHistory;
import com.sellsync.api.domain.shipment.repository.ShipmentUploadHistoryRepository;
import com.sellsync.api.domain.shipment.service.ShipmentExcelService;
import com.sellsync.api.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shipments")
@RequiredArgsConstructor
@Slf4j
public class ShipmentExcelController {

    private final ShipmentExcelService excelService;
    private final ShipmentUploadHistoryRepository uploadHistoryRepository;

    /**
     * 송장 엑셀 업로드
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<ShipmentExcelService.UploadResult>> uploadExcel(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam("file") MultipartFile file) {

        log.info("[Shipment] Excel upload by {} - file: {}", user.getUserId(), file.getOriginalFilename());

        // 파일 검증
        if (file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어있습니다");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            throw new IllegalArgumentException("엑셀 파일만 업로드 가능합니다 (.xlsx, .xls)");
        }

        ShipmentExcelService.UploadResult result = excelService.processExcel(
                user.getTenantId(), file, user.getUserId());

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 업로드 이력 조회
     */
    @GetMapping("/upload/history")
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<List<ShipmentUploadHistory>>> getUploadHistory(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<ShipmentUploadHistory> historyPage = uploadHistoryRepository
                .findByTenantIdOrderByCreatedAtDesc(user.getTenantId(), PageRequest.of(page, size));

        return ResponseEntity.ok(ApiResponse.ok(historyPage.getContent()));
    }

    /**
     * 업로드 상세 조회
     */
    @GetMapping("/upload/history/{uploadId}")
    @PreAuthorize("hasAnyRole('VIEWER', 'OPERATOR', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<ShipmentUploadHistory>> getUploadDetail(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID uploadId) {

        ShipmentUploadHistory history = uploadHistoryRepository.findById(uploadId)
                .filter(h -> h.getTenantId().equals(user.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Upload history not found"));

        return ResponseEntity.ok(ApiResponse.ok(history));
    }
}
