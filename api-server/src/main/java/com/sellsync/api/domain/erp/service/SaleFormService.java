package com.sellsync.api.domain.erp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellsync.api.domain.erp.dto.*;
import com.sellsync.api.domain.erp.entity.SaleFormLine;
import com.sellsync.api.domain.erp.entity.SaleFormLine.SaleFormLineStatus;
import com.sellsync.api.domain.erp.entity.SaleFormTemplate;
import com.sellsync.api.domain.erp.repository.SaleFormLineRepository;
import com.sellsync.api.domain.erp.repository.SaleFormTemplateRepository;
import com.sellsync.infra.erp.ecount.EcountClient;
import com.sellsync.infra.erp.ecount.dto.EcountSaleFormDto;
import com.sellsync.infra.erp.ecount.dto.EcountSaleFormRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 전표입력 서비스
 * - 템플릿 관리 (CRUD)
 * - 전표 라인 관리 (CRUD)
 * - 전표 입력 (ERP API 호출)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SaleFormService {

    private final SaleFormTemplateRepository templateRepository;
    private final SaleFormLineRepository lineRepository;
    private final EcountClient ecountClient;

    // ======================== 템플릿 관리 ========================

    /**
     * 템플릿 목록 조회 (시스템 템플릿 포함)
     */
    @Transactional(readOnly = true)
    public List<SaleFormTemplateDto> getTemplates(UUID tenantId) {
        return templateRepository.findByTenantIdIncludingSystemTemplates(tenantId)
                .stream()
                .map(this::toTemplateDto)
                .collect(Collectors.toList());
    }

    /**
     * 기본 템플릿 조회 (시스템 템플릿 포함)
     */
    @Transactional(readOnly = true)
    public SaleFormTemplateDto getDefaultTemplate(UUID tenantId) {
        return templateRepository.findDefaultTemplateIncludingSystem(tenantId)
                .map(this::toTemplateDto)
                .orElse(null);
    }

    /**
     * 템플릿 생성
     */
    @Transactional
    public SaleFormTemplateDto createTemplate(UUID tenantId, CreateSaleFormTemplateRequest request) {
        // 기본 템플릿으로 설정하는 경우, 기존 기본 템플릿 해제
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            templateRepository.findByTenantIdAndIsDefaultTrueAndIsActiveTrue(tenantId)
                    .ifPresent(template -> {
                        template.unsetDefault();
                        templateRepository.save(template);
                    });
        }

        SaleFormTemplate template = SaleFormTemplate.builder()
                .tenantId(tenantId)
                .templateName(request.getTemplateName())
                .isDefault(request.getIsDefault() != null ? request.getIsDefault() : false)
                .description(request.getDescription())
                .defaultCustomerCode(request.getDefaultCustomerCode())
                .defaultWarehouseCode(request.getDefaultWarehouseCode())
                .defaultIoType(request.getDefaultIoType())
                .defaultEmpCd(request.getDefaultEmpCd())
                .defaultSite(request.getDefaultSite())
                .defaultExchangeType(request.getDefaultExchangeType())
                .templateConfig(request.getTemplateConfig())
                .isActive(true)
                .build();

        template = templateRepository.save(template);
        log.info("[SaleForm] Template created: tenantId={}, templateId={}, name={}", 
                tenantId, template.getId(), template.getTemplateName());

        return toTemplateDto(template);
    }

    /**
     * 템플릿 수정
     */
    @Transactional
    public SaleFormTemplateDto updateTemplate(UUID tenantId, UUID templateId, CreateSaleFormTemplateRequest request) {
        SaleFormTemplate template = templateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));
        
        // 시스템 템플릿은 수정 불가
        if (Boolean.TRUE.equals(template.getIsSystemTemplate())) {
            throw new IllegalArgumentException("시스템 템플릿은 수정할 수 없습니다");
        }

        // 기본 템플릿으로 설정하는 경우, 기존 기본 템플릿 해제
        if (Boolean.TRUE.equals(request.getIsDefault()) && !template.getIsDefault()) {
            templateRepository.findByTenantIdAndIsDefaultTrueAndIsActiveTrue(tenantId)
                    .ifPresent(existing -> {
                        existing.unsetDefault();
                        templateRepository.save(existing);
                    });
        }

        template.setTemplateName(request.getTemplateName());
        template.setIsDefault(request.getIsDefault() != null ? request.getIsDefault() : false);
        template.setDescription(request.getDescription());
        template.setDefaultCustomerCode(request.getDefaultCustomerCode());
        template.setDefaultWarehouseCode(request.getDefaultWarehouseCode());
        template.setDefaultIoType(request.getDefaultIoType());
        template.setDefaultEmpCd(request.getDefaultEmpCd());
        template.setDefaultSite(request.getDefaultSite());
        template.setDefaultExchangeType(request.getDefaultExchangeType());
        template.setTemplateConfig(request.getTemplateConfig());

        template = templateRepository.save(template);
        log.info("[SaleForm] Template updated: templateId={}", templateId);

        return toTemplateDto(template);
    }

    /**
     * 템플릿 삭제 (소프트 삭제)
     */
    @Transactional
    public void deleteTemplate(UUID tenantId, UUID templateId) {
        SaleFormTemplate template = templateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + templateId));
        
        // 시스템 템플릿은 삭제 불가
        if (Boolean.TRUE.equals(template.getIsSystemTemplate())) {
            throw new IllegalArgumentException("시스템 템플릿은 삭제할 수 없습니다");
        }

        template.setIsActive(false);
        templateRepository.save(template);
        log.info("[SaleForm] Template deleted: templateId={}", templateId);
    }

    // ======================== 전표 라인 관리 ========================

    /**
     * 전표 라인 목록 조회
     */
    @Transactional(readOnly = true)
    public Page<SaleFormLineDto> getLines(UUID tenantId, Pageable pageable) {
        return lineRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable)
                .map(this::toLineDto);
    }

    /**
     * 상태별 전표 라인 조회
     */
    @Transactional(readOnly = true)
    public Page<SaleFormLineDto> getLinesByStatus(UUID tenantId, SaleFormLineStatus status, Pageable pageable) {
        return lineRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status, pageable)
                .map(this::toLineDto);
    }

    /**
     * 전표 라인 생성
     */
    @Transactional
    public SaleFormLineDto createLine(UUID tenantId, CreateSaleFormLineRequest request) {
        SaleFormLine line = SaleFormLine.builder()
                .tenantId(tenantId)
                .ioDate(request.getIoDate())
                .cust(request.getCust())
                .custDes(request.getCustDes())
                .empCd(request.getEmpCd())
                .whCd(request.getWhCd())
                .ioType(request.getIoType())
                .prodCd(request.getProdCd())
                .prodDes(request.getProdDes())
                .sizeDes(request.getSizeDes())
                .qty(request.getQty())
                .price(request.getPrice())
                .supplyAmt(request.getSupplyAmt())
                .vatAmt(request.getVatAmt())
                .remarks(request.getRemarks())
                .site(request.getSite())
                .pjtCd(request.getPjtCd())
                .formData(request.getFormData())
                .status(SaleFormLineStatus.DRAFT)
                .build();

        line = lineRepository.save(line);
        log.info("[SaleForm] Line created: tenantId={}, lineId={}, prodCd={}", 
                tenantId, line.getId(), line.getProdCd());

        return toLineDto(line);
    }

    /**
     * 전표 라인 수정
     */
    @Transactional
    public SaleFormLineDto updateLine(UUID tenantId, UUID lineId, CreateSaleFormLineRequest request) {
        SaleFormLine line = lineRepository.findByIdAndTenantId(lineId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Line not found: " + lineId));

        // 이미 전표가 입력된 라인은 수정 불가
        if (line.getStatus() == SaleFormLineStatus.POSTED) {
            throw new IllegalStateException("Cannot update posted line: " + lineId);
        }

        line.setIoDate(request.getIoDate());
        line.setCust(request.getCust());
        line.setCustDes(request.getCustDes());
        line.setEmpCd(request.getEmpCd());
        line.setWhCd(request.getWhCd());
        line.setIoType(request.getIoType());
        line.setProdCd(request.getProdCd());
        line.setProdDes(request.getProdDes());
        line.setSizeDes(request.getSizeDes());
        line.setQty(request.getQty());
        line.setPrice(request.getPrice());
        line.setSupplyAmt(request.getSupplyAmt());
        line.setVatAmt(request.getVatAmt());
        line.setRemarks(request.getRemarks());
        line.setSite(request.getSite());
        line.setPjtCd(request.getPjtCd());
        line.setFormData(request.getFormData());

        line = lineRepository.save(line);
        log.info("[SaleForm] Line updated: lineId={}", lineId);

        return toLineDto(line);
    }

    /**
     * 전표 라인 삭제
     */
    @Transactional
    public void deleteLine(UUID tenantId, UUID lineId) {
        SaleFormLine line = lineRepository.findByIdAndTenantId(lineId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Line not found: " + lineId));

        // 이미 전표가 입력된 라인은 삭제 불가
        if (line.getStatus() == SaleFormLineStatus.POSTED) {
            throw new IllegalStateException("Cannot delete posted line: " + lineId);
        }

        lineRepository.delete(line);
        log.info("[SaleForm] Line deleted: lineId={}", lineId);
    }

    // ======================== 전표 입력 ========================

    /**
     * 선택한 라인들을 전표로 입력
     * - mergeToSingleDocument=true: 한 전표로 묶음 (같은 UPLOAD_SER_NO)
     * - mergeToSingleDocument=false: 각각 별도 전표로 생성
     */
    @Transactional
    public ErpPostingResult postSaleForms(UUID tenantId, PostSaleFormsRequest request) {
        // 라인 조회
        List<SaleFormLine> lines = lineRepository.findByTenantIdAndIdIn(tenantId, request.getLineIds());
        
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("No lines found for the given IDs");
        }

        // 이미 입력된 라인이 있는지 체크
        List<UUID> postedLineIds = lines.stream()
                .filter(line -> line.getStatus() == SaleFormLineStatus.POSTED)
                .map(SaleFormLine::getId)
                .collect(Collectors.toList());
        
        if (!postedLineIds.isEmpty()) {
            throw new IllegalStateException("Some lines are already posted: " + postedLineIds);
        }

        // UPLOAD_SER_NO 생성 (SMALLINT 4자리: 0~9999)
        int baseUploadSerNo = Math.abs(UUID.randomUUID().hashCode()) % 10000;
        
        // EcountSaleFormDto 리스트 생성
        List<EcountSaleFormDto> forms = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            SaleFormLine line = lines.get(i);
            
            // 한 전표로 묶을지 여부에 따라 UPLOAD_SER_NO 설정
            int lineUploadSerNo = Boolean.TRUE.equals(request.getMergeToSingleDocument()) 
                    ? baseUploadSerNo 
                    : (baseUploadSerNo + i) % 10000;  // 4자리 범위 유지
            
            EcountSaleFormDto form = convertToEcountForm(line, lineUploadSerNo);
            forms.add(form);
            
            // 라인 상태 업데이트 (PENDING)
            line.setStatus(SaleFormLineStatus.PENDING);
            line.setUploadSerNo(String.valueOf(lineUploadSerNo));  // DB 저장용 문자열 변환
        }

        lineRepository.saveAll(lines);

        // ERP API 호출
        EcountSaleFormRequest ecountRequest = EcountSaleFormRequest.of(forms);
        ErpPostingResult result = ecountClient.saveSaleForms(tenantId, ecountRequest);

        // 결과 처리
        if (result.isSuccess()) {
            for (SaleFormLine line : lines) {
                line.markAsPosted(result.getDocumentNo(), result.getRawResponse());
            }
            log.info("[SaleForm] Sales posted successfully: tenantId={}, lineCount={}, docNo={}", 
                    tenantId, lines.size(), result.getDocumentNo());
        } else {
            for (SaleFormLine line : lines) {
                line.markAsFailed(result.getErrorMessage());
            }
            log.warn("[SaleForm] Sales posting failed: tenantId={}, error={}", 
                    tenantId, result.getErrorMessage());
        }

        lineRepository.saveAll(lines);

        return result;
    }

    // ======================== Private Methods ========================

    private SaleFormTemplateDto toTemplateDto(SaleFormTemplate template) {
        return SaleFormTemplateDto.builder()
                .id(template.getId())
                .tenantId(template.getTenantId())
                .templateName(template.getTemplateName())
                .isDefault(template.getIsDefault())
                .isSystemTemplate(template.getIsSystemTemplate())
                .description(template.getDescription())
                .defaultCustomerCode(template.getDefaultCustomerCode())
                .defaultWarehouseCode(template.getDefaultWarehouseCode())
                .defaultIoType(template.getDefaultIoType())
                .defaultEmpCd(template.getDefaultEmpCd())
                .defaultSite(template.getDefaultSite())
                .defaultExchangeType(template.getDefaultExchangeType())
                .templateConfig(template.getTemplateConfig())
                .isActive(template.getIsActive())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }

    private SaleFormLineDto toLineDto(SaleFormLine line) {
        return SaleFormLineDto.builder()
                .id(line.getId())
                .tenantId(line.getTenantId())
                .uploadSerNo(line.getUploadSerNo())
                .ioDate(line.getIoDate())
                .cust(line.getCust())
                .custDes(line.getCustDes())
                .empCd(line.getEmpCd())
                .whCd(line.getWhCd())
                .ioType(line.getIoType())
                .prodCd(line.getProdCd())
                .prodDes(line.getProdDes())
                .sizeDes(line.getSizeDes())
                .qty(line.getQty())
                .price(line.getPrice())
                .supplyAmt(line.getSupplyAmt())
                .vatAmt(line.getVatAmt())
                .remarks(line.getRemarks())
                .site(line.getSite())
                .pjtCd(line.getPjtCd())
                .formData(line.getFormData())
                .status(line.getStatus())
                .docNo(line.getDocNo())
                .erpResponse(line.getErpResponse())
                .createdAt(line.getCreatedAt())
                .updatedAt(line.getUpdatedAt())
                .build();
    }

    private EcountSaleFormDto convertToEcountForm(SaleFormLine line, int uploadSerNo) {
        return EcountSaleFormDto.builder()
                .uploadSerNo(String.valueOf(uploadSerNo))
                .ioDate(line.getIoDate())
                .cust(line.getCust())
                .custDes(line.getCustDes())
                .empCd(line.getEmpCd() != null ? line.getEmpCd() : "")
                .whCd(line.getWhCd() != null ? line.getWhCd() : "00009")
                .ioType(line.getIoType() != null ? line.getIoType() : "")
                .prodCd(line.getProdCd())
                .prodDes(line.getProdDes())
                .sizeDes(line.getSizeDes())
                .qty(line.getQty())
                .price(line.getPrice())
                .supplyAmt(line.getSupplyAmt())
                .vatAmt(line.getVatAmt())
                .remarks(line.getRemarks())
                .site(line.getSite())
                .pjtCd(line.getPjtCd())
                .build();
    }
}
