package com.sellsync.api.domain.erp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "erp_document_lines")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErpDocumentLine {

    @Id
    @Column(name = "line_id")
    private UUID lineId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private ErpDocument document;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(name = "item_code", nullable = false, length = 50)
    private String itemCode;

    @Column(name = "item_name", length = 200)
    private String itemName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "vat_amount", nullable = false)
    private Long vatAmount;

    @Column(name = "warehouse_code", length = 50)
    private String warehouseCode;

    @Column(name = "order_item_id")
    private UUID orderItemId;

    @PrePersist
    public void prePersist() {
        if (lineId == null) lineId = UUID.randomUUID();
        if (quantity == null) quantity = 1;
        if (unitPrice == null) unitPrice = 0L;
        if (amount == null) amount = 0L;
        if (vatAmount == null) vatAmount = 0L;
    }
}
