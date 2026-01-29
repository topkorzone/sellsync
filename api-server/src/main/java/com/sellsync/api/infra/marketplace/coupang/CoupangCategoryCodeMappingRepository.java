package com.sellsync.api.infra.marketplace.coupang;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CoupangCategoryCodeMappingRepository extends JpaRepository<CoupangCategoryCodeMapping, UUID> {

    /**
     * displayCategoryCode로 매핑 조회 (commissionRate FK를 fetch join으로 즉시 로딩)
     *
     * commissionRate가 LAZY이므로, Caffeine 캐시 로더 등 트랜잭션 외부에서
     * getEffectiveRate() 호출 시 LazyInitializationException 방지
     */
    @Query("SELECT m FROM CoupangCategoryCodeMapping m LEFT JOIN FETCH m.commissionRate WHERE m.displayCategoryCode = :code")
    Optional<CoupangCategoryCodeMapping> findByDisplayCategoryCode(@Param("code") String displayCategoryCode);
}
