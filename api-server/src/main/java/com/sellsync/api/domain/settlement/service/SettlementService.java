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
     * 전략: 먼저 조회 시도 → 없으면 생성
     * (이전 방식의 try-catch는 트랜잭션 커밋 시점 오류를 잡지 못함)
     * 
     * @param request 정산 배치 생성 요청
     * @return 생성/조회된 정산 배치
     */
    @Transactional
    public SettlementBatchResponse createOrGet(CreateSettlementBatchRequest request) {
        log.info("[정산 배치 생성/조회] tenantId={}, marketplace={}, cycle={}", 
            request.getTenantId(), request.getMarketplace(), request.getSettlementCycle());

        // 1. 먼저 기존 배치 조회
        return settlementBatchRepository
                .findByTenantIdAndMarketplaceAndSettlementCycle(
                    request.getTenantId(),
                    request.getMarketplace(),
                    request.getSettlementCycle()
                )
                .map(existing -> {
                    log.info("[정산 배치 이미 존재] settlementBatchId={}, status={}", 
                        existing.getSettlementBatchId(), 
                        existing.getSettlementStatus());
                    return SettlementBatchResponse.from(existing);
                })
                .orElseGet(() -> {
                    // 2. 없으면 신규 생성
                    log.info("[정산 배치 신규 생성] cycle={}", request.getSettlementCycle());
                    
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
                });
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
     * 전표 ID 연결 (전표가 생성되면 호출)
     * 상태 전이 없이 전표 ID만 저장
     */
    @Transactional
    public SettlementBatchResponse linkPostings(UUID settlementBatchId, 
                                                UUID commissionPostingId, 
                                                UUID receiptPostingId) {
        SettlementBatch batch = settlementBatchRepository.findById(settlementBatchId)
                .orElseThrow(() -> new SettlementBatchNotFoundException(settlementBatchId));

        batch.linkPostings(commissionPostingId, receiptPostingId);
        SettlementBatch updated = settlementBatchRepository.save(batch);

        log.info("[전표 연결] settlementBatchId={}, commissionPostingId={}, receiptPostingId={}", 
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
