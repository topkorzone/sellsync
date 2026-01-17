package com.sellsync.api.domain.posting.repository;

import com.sellsync.api.domain.posting.entity.PostingTemplateField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 전표 템플릿 필드 Repository
 */
@Repository
public interface PostingTemplateFieldRepository extends JpaRepository<PostingTemplateField, UUID> {
    
    /**
     * 템플릿의 모든 필드 조회 (순서대로)
     */
    List<PostingTemplateField> findByTemplate_TemplateIdOrderByDisplayOrderAsc(UUID templateId);
    
    /**
     * 템플릿의 필드를 매핑까지 함께 조회
     */
    @Query("""
        SELECT f FROM PostingTemplateField f
        LEFT JOIN FETCH f.mapping
        WHERE f.template.templateId = :templateId
        ORDER BY f.displayOrder ASC
        """)
    List<PostingTemplateField> findByTemplateIdWithMapping(@Param("templateId") UUID templateId);
    
    /**
     * 필드 ID로 매핑까지 함께 조회
     */
    @Query("""
        SELECT f FROM PostingTemplateField f
        LEFT JOIN FETCH f.mapping
        WHERE f.fieldId = :fieldId
        """)
    Optional<PostingTemplateField> findByIdWithMapping(@Param("fieldId") UUID fieldId);
    
    /**
     * 템플릿의 필수 필드 목록
     */
    List<PostingTemplateField> findByTemplate_TemplateIdAndIsRequiredTrueOrderByDisplayOrderAsc(UUID templateId);
}
