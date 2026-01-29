package com.sellsync.api.infra.marketplace.coupang;

import com.sellsync.api.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "coupang_commission_rates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CoupangCommissionRate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "major_category", nullable = false, length = 50)
    private String majorCategory;

    @Column(name = "middle_category", length = 100)
    private String middleCategory;

    @Column(name = "minor_category", length = 100)
    private String minorCategory;

    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionRate;
}
