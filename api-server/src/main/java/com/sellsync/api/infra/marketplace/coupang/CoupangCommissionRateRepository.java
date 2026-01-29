package com.sellsync.api.infra.marketplace.coupang;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CoupangCommissionRateRepository extends JpaRepository<CoupangCommissionRate, UUID> {

    List<CoupangCommissionRate> findByMajorCategory(String majorCategory);
}
