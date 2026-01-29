package com.sellsync.api.domain.subscription.repository;

import com.sellsync.api.domain.subscription.entity.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {

    List<PaymentMethod> findByTenantId(UUID tenantId);

    Optional<PaymentMethod> findByTenantIdAndIsDefaultTrue(UUID tenantId);
}
