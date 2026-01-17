package com.sellsync.api.domain.posting.repository;

import com.sellsync.api.domain.posting.entity.PostingTemplate;
import com.sellsync.api.domain.posting.enums.PostingType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 전표 템플릿 Repository
 */
@Repository
public interface PostingTemplateRepository extends JpaRepository<PostingTemplate, UUID> {
    
    /**
     * tenant의 모든 템플릿 조회
     */
    List<PostingTemplate> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    
    /**
     * tenant의 특정 ERP, postingType 템플릿 목록
     */
    List<PostingTemplate> findByTenantIdAndErpCodeAndPostingTypeOrderByCreatedAtDesc(
        UUID tenantId, 
        String erpCode, 
        PostingType postingType
    );
    
    /**
     * 활성 템플릿 조회
     * postingType별로 하나만 활성화 가능
     */
    @Query("""
        SELECT t FROM PostingTemplate t
        WHERE t.tenantId = :tenantId
        AND t.erpCode = :erpCode
        AND t.postingType = :postingType
        AND t.isActive = true
        """)
    Optional<PostingTemplate> findActiveTemplate(
        @Param("tenantId") UUID tenantId,
        @Param("erpCode") String erpCode,
        @Param("postingType") PostingType postingType
    );
    
    /**
     * 활성 템플릿 존재 여부 확인
     */
    boolean existsByTenantIdAndErpCodeAndPostingTypeAndIsActive(
        UUID tenantId,
        String erpCode,
        PostingType postingType,
        Boolean isActive
    );
    
    /**
     * tenant의 특정 ERP 모든 템플릿
     */
    List<PostingTemplate> findByTenantIdAndErpCodeOrderByCreatedAtDesc(
        UUID tenantId,
        String erpCode
    );
    
    /**
     * 템플릿 ID로 필드까지 fetch join 조회
     */
    @Query("""
        SELECT DISTINCT t FROM PostingTemplate t
        LEFT JOIN FETCH t.fields f
        LEFT JOIN FETCH f.mapping
        WHERE t.templateId = :templateId
        """)
    Optional<PostingTemplate> findByIdWithFields(@Param("templateId") UUID templateId);
}
