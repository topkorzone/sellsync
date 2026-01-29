package com.sellsync.api.infra.marketplace.coupang;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CoupangDisplayCategoryRepository extends JpaRepository<CoupangDisplayCategory, UUID> {

    Optional<CoupangDisplayCategory> findByDisplayCategoryCode(String displayCategoryCode);

    List<CoupangDisplayCategory> findByDepth(Integer depth);

    boolean existsByDisplayCategoryCode(String displayCategoryCode);
}
