package com.sellsync.api.domain.shipment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipment_upload_histories")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentUploadHistory {

    @Id
    @Column(name = "upload_id")
    private UUID uploadId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_size")
    private Integer fileSize;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "total_rows")
    private Integer totalRows;

    @Column(name = "success_count")
    private Integer successCount;

    @Column(name = "failed_count")
    private Integer failedCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_details", columnDefinition = "jsonb")
    private String errorDetails;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @PrePersist
    public void prePersist() {
        if (uploadId == null) uploadId = UUID.randomUUID();
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = "PROCESSING";
        if (totalRows == null) totalRows = 0;
        if (successCount == null) successCount = 0;
        if (failedCount == null) failedCount = 0;
    }
}
