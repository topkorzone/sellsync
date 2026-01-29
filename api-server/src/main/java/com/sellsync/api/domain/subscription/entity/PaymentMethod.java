package com.sellsync.api.domain.subscription.entity;

import com.sellsync.api.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "payment_methods")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentMethod extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "payment_method_id")
    private UUID paymentMethodId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "billing_key", nullable = false, length = 255)
    private String billingKey;

    @Column(name = "card_company", length = 50)
    private String cardCompany;

    @Column(name = "card_number", length = 20)
    private String cardNumber;

    @Column(name = "card_type", length = 20)
    private String cardType;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;
}
