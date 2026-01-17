package com.sellsync.api.domain.erp.repository;

import com.sellsync.api.domain.erp.entity.ErpDocument;
import com.sellsync.api.domain.erp.enums.PostingStatus;
import com.sellsync.api.domain.erp.enums.PostingType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ErpDocumentRepository extends JpaRepository<ErpDocument, UUID> {

    Optional<ErpDocument> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    List<ErpDocument> findByOrderId(UUID orderId);

    Optional<ErpDocument> findByOrderIdAndPostingType(UUID orderId, PostingType postingType);

    Page<ErpDocument> findByTenantIdAndPostingStatusOrderByUpdatedAtDesc(
            UUID tenantId, PostingStatus status, Pageable pageable);

    Page<ErpDocument> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT d FROM ErpDocument d WHERE d.tenantId = :tenantId " +
           "AND d.postingStatus = 'FAILED' AND d.retryCount < :maxRetry")
    List<ErpDocument> findRetryableDocuments(@Param("tenantId") UUID tenantId, 
                                              @Param("maxRetry") int maxRetry);

    long countByTenantIdAndPostingStatus(UUID tenantId, PostingStatus status);
}
