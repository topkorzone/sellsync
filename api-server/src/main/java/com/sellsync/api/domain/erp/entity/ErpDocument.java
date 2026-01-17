package com.sellsync.api.domain.erp.entity;

import com.sellsync.api.domain.erp.enums.PostingStatus;
import com.sellsync.api.domain.erp.enums.PostingType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "erp_documents")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErpDocument {

    @Id
    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "erp_code", nullable = false, length = 20)
    private String erpCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "posting_type", nullable = false, length = 30)
    private PostingType postingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "posting_status", nullable = false, length = 30)
    private PostingStatus postingStatus;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "document_date", nullable = false)
    private LocalDate documentDate;

    @Column(name = "customer_code", length = 50)
    private String customerCode;

    @Column(name = "warehouse_code", length = 20)
    private String warehouseCode;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "total_vat", nullable = false)
    private Long totalVat;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "erp_doc_no", length = 50)
    private String erpDocNo;

    @Column(name = "original_document_id")
    private UUID originalDocumentId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload", columnDefinition = "jsonb")
    private String requestPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    private String responsePayload;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "last_attempted_at")
    private LocalDateTime lastAttemptedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ErpDocumentLine> lines = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (documentId == null) documentId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (postingStatus == null) postingStatus = PostingStatus.CREATED;
        if (totalAmount == null) totalAmount = 0L;
        if (totalVat == null) totalVat = 0L;
        if (retryCount == null) retryCount = 0;
        if (erpCode == null) erpCode = "ECOUNT";
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addLine(ErpDocumentLine line) {
        lines.add(line);
        line.setDocument(this);
    }
}
