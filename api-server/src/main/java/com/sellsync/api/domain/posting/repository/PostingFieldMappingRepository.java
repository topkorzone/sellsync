package com.sellsync.api.domain.posting.repository;

import com.sellsync.api.domain.posting.entity.PostingFieldMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 필드 매핑 Repository
 */
@Repository
public interface PostingFieldMappingRepository extends JpaRepository<PostingFieldMapping, UUID> {
    
    /**
     * 필드 ID로 매핑 조회
     */
    Optional<PostingFieldMapping> findByField_FieldId(UUID fieldId);
    
    /**
     * 필드 ID로 매핑 삭제
     */
    void deleteByField_FieldId(UUID fieldId);
}
