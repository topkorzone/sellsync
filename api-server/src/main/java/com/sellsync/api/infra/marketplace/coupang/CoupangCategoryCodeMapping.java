package com.sellsync.api.infra.marketplace.coupang;

import com.sellsync.api.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "coupang_category_code_mappings")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CoupangCategoryCodeMapping extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "display_category_code", nullable = false, unique = true, length = 20)
    private String displayCategoryCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commission_rate_id")
    private CoupangCommissionRate commissionRate;

    @Column(name = "override_rate", precision = 5, scale = 2)
    private BigDecimal overrideRate;

    @Column(name = "memo", length = 200)
    private String memo;

    /**
     * 실제 적용 수수료율: overrideRate 우선, 없으면 참조 테이블 rate
     */
    public BigDecimal getEffectiveRate() {
        if (overrideRate != null) return overrideRate;
        if (commissionRate != null) return commissionRate.getCommissionRate();
        return null;
    }
}
