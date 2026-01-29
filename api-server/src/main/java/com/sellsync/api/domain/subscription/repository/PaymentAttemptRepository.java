package com.sellsync.api.domain.subscription.repository;

import com.sellsync.api.domain.subscription.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    List<PaymentAttempt> findByInvoice_InvoiceIdOrderByAttemptedAtDesc(UUID invoiceId);
}
