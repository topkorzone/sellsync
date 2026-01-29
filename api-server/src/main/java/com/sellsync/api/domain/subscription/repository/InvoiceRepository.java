package com.sellsync.api.domain.subscription.repository;

import com.sellsync.api.domain.subscription.entity.Invoice;
import com.sellsync.api.domain.subscription.enums.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Page<Invoice> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    List<Invoice> findByStatusAndNextRetryAtBefore(InvoiceStatus status, LocalDateTime date);
}
