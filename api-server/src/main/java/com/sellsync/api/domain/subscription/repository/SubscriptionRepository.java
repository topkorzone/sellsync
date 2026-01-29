package com.sellsync.api.domain.subscription.repository;

import com.sellsync.api.domain.subscription.entity.Subscription;
import com.sellsync.api.domain.subscription.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByTenantId(UUID tenantId);

    List<Subscription> findByStatusAndTrialEndDateBefore(SubscriptionStatus status, LocalDateTime date);

    List<Subscription> findByStatusIn(List<SubscriptionStatus> statuses);

    @Query("SELECT s FROM Subscription s WHERE s.status = 'ACTIVE' AND s.billingAnchorDay = :day AND s.cancelAtPeriodEnd = false")
    List<Subscription> findDueForBilling(@Param("day") int day);

    @Query("SELECT s FROM Subscription s WHERE s.status = 'ACTIVE' AND s.currentPeriodEnd <= :date AND s.cancelAtPeriodEnd = true")
    List<Subscription> findCancelledAtPeriodEnd(@Param("date") LocalDateTime date);
}
