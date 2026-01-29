package com.sellsync.api.domain.subscription.repository;

import com.sellsync.api.domain.subscription.entity.SubscriptionPlan;
import com.sellsync.api.domain.subscription.enums.PlanCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

    Optional<SubscriptionPlan> findByPlanCode(PlanCode planCode);

    List<SubscriptionPlan> findByIsActiveTrueOrderByDisplayOrder();
}
