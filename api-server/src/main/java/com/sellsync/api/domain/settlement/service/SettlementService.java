package com.sellsync.api.domain.settlement.service;

import com.sellsync.api.domain.settlement.dto.CreateSettlementBatchRequest;
import com.sellsync.api.domain.settlement.dto.SettlementBatchResponse;
import com.sellsync.api.domain.settlement.entity.SettlementBatch;
import com.sellsync.api.domain.settlement.enums.SettlementStatus;
import com.sellsync.api.domain.settlement.exception.InvalidSettlementStateException;
import com.sellsync.api.domain.settlement.exception.SettlementBatchNotFoundException;
import com.sellsync.api.domain.settlement.repository.SettlementBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 정산 배치 서비스
 * 
 * 역할:
 * - 정산 배치 생성/조회/상태 관리
 * - 멱등성 보장 (ADR-0001)
 * - 상태머신 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementBatchRepository settlementBatchRepository;

    /**
     * 정산 배치 생성 또는 조회 (멱등성 보장)
     * 
     * @param request 정산 배치 생성 요청
     * @return 생성/조회된 정산 배치
     */
    @Transactional
    public SettlementBatchResponse createOrGet(CreateSettlementBatchRequest request) {
        log.info("[정산 배치 생성/조회] tenantId={}, marketplace={}, cycle={}", 
            request.getTenantId(), request.getMarketplace(), request.getSettlementCycle());

        try {
            // 1. 신규 배치 생성
            SettlementBatch batch = SettlementBatch.builder()
                    .tenantId(request.getTenantId())
                    .marketplace(request.getMarketplace())
                    .settlementCycle(request.getSettlementCycle())
                    .settlementPeriodStart(request.getSettlementPeriodStart())
                    .settlementPeriodEnd(request.getSettlementPeriodEnd())
                    .marketplaceSettlementId(request.getMarketplaceSettlementId())
                    .marketplacePayload(request.getMarketplacePayload())
                    .settlementStatus(SettlementStatus.COLLECTED)
                    .build();

            SettlementBatch saved = settlementBatchRepository.save(batch);
            
            log.info("[정산 배치 생성 완료] settlementBatchId={}", saved.getSettlementBatchId());
            
            return SettlementBatchResponse.from(saved);

        } catch (DataIntegrityViolationException e) {
            // 2. 멱등성 제약 위반 → 기존 배치 조회
            log.info("[정산 배치 이미 존재] 기존 배치 조회");
            
            SettlementBatch existing = settlementBatchRepository
                    .findByTenantIdAndMarketplaceAndSettlementCycle(
                        request.getTenantId(),
                        request.getMarketplace(),
                        request.getSettlementCycle()
                    )
                    .orElseThrow(() -> new SettlementBatchNotFoundException(
                        "Settlement batch not found after constraint violation"
                    ));

            log.info("[기존 정산 배치 조회 완료] settlementBatchId={}, status={}", 
                existing.getSettlementBatchId(), existing.getSettlementStatus());

            return SettlementBatchResponse.from(existing);
        }
    }

    /**
     * 정산 배치 조회
     */
    @Transactional(readOnly = true)
    public SettlementBatchResponse getById(UUID settlementBatchId) {
        SettlementBatch batch = settlementBatchRepository.findById(settlementBatchId)
                .orElseThrow(() -> new SettlementBatchNotFoundException(settlementBatchId));
        
        return SettlementBatchResponse.from(batch);
    }

    /**
     * 상태 전이
     */
    @Transactional
    public SettlementBatchResponse transitionTo(UUID settlementBatchId, SettlementStatus targetStatus) {
        SettlementBatch batch = settlementBatchRepository.findById(settlementBatchId)
                .orElseThrow(() -> new SettlementBatchNotFoundException(settlementBatchId));

        if (!batch.getSettlementStatus().canTransitionTo(targetStatus)) {
            throw new InvalidSettlementStateException(
                String.format("Invalid state transition: %s -> %s for settlementBatchId=%s",
                    batch.getSettlementStatus(), targetStatus, settlementBatchId)
            );
        }

        batch.transitionTo(targetStatus);
        SettlementBatch updated = settlementBatchRepository.save(batch);
        
        log.info("[상태 전이] settlementBatchId={}, {} -> {}", 
            settlementBatchId, batch.getSettlementStatus(), targetStatus);

        return SettlementBatchResponse.from(updated);
    }

    /**
     * 검증 완료 처리
     */
    @Transactional
    public SettlementBatchResponse markAsValidated(UUID settlementBatchId) {
        SettlementBatch batch = settlementBatchRepository.findById(settlementBatchId)
                .orElseThrow(() -> new SettlementBatchNotFoundException(settlementBatchId));

        batch.markAsValidated();
        SettlementBatch updated = settlementBatchRepository.save(batch);

        log.info("[검증 완료] settlementBatchId={}, status=VALIDATED", settlementBatchId);

        return SettlementBatchResponse.from(updated);
    }

    /**
     * 전표 준비 완료 처리
     */
    @Transactional
    public SettlementBatchResponse markAsPostingReady(UUID settlementBatchId) {
        SettlementBatch batch = settlementBatchRepository.findById(settlementBatchId)
                .orElseThrow(() -> new SettlementBatchNotFoundException(settlementBatchId));

        batch.markAsPostingReady();
        SettlementBatch updated = settlementBatchRepository.save(batch);

        log.info("[전표 준비 완료] settlementBatchId={}, status=POSTING_READY", settlementBatchId);

        return SettlementBatchResponse.from(updated);
    }

    /**
     * 전표 생성 완료 처리
     */
    @Transactional
    public SettlementBatchResponse markAsPosted(UUID settlementBatchId, 
                                                UUID commissionPostingId, 
                                                UUID receiptPostingId) {
        SettlementBatch batch = settlementBatchRepository.findById(settlementBatchId)
                .orElseThrow(() -> new SettlementBatchNotFoundException(settlementBatchId));

        batch.markAsPosted(commissionPostingId, receiptPostingId);
        SettlementBatch updated = settlementBatchRepository.save(batch);

        log.info("[전표 생성 완료] settlementBatchId={}, status=POSTED, commissionPostingId={}, receiptPostingId={}", 
            settlementBatchId, commissionPostingId, receiptPostingId);

        return SettlementBatchResponse.from(updated);
    }

    /**
     * 정산 완료 처리
     */
    @Transactional
    public SettlementBatchResponse markAsClosed(UUID settlementBatchId) {
        SettlementBatch batch = settlementBatchRepository.findById(settlementBatchId)
                .orElseThrow(() -> new SettlementBatchNotFoundException(settlementBatchId));

        batch.markAsClosed();
        SettlementBatch updated = settlementBatchRepository.save(batch);

        log.info("[정산 완료] settlementBatchId={}, status=CLOSED", settlementBatchId);

        return SettlementBatchResponse.from(updated);
    }

    /**
     * 실패 처리
     */
    @Transactional
    public SettlementBatchResponse markAsFailed(UUID settlementBatchId, 
                                               String errorCode, 
                                               String errorMessage) {
        SettlementBatch batch = settlementBatchRepository.findById(settlementBatchId)
                .orElseThrow(() -> new SettlementBatchNotFoundException(settlementBatchId));

        batch.markAsFailed(errorCode, errorMessage);
        SettlementBatch updated = settlementBatchRepository.save(batch);

        log.error("[정산 실패] settlementBatchId={}, status=FAILED, errorCode={}", 
            settlementBatchId, errorCode);

        return SettlementBatchResponse.from(updated);
    }
}
