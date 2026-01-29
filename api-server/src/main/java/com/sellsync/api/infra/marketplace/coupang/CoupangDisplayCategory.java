package com.sellsync.api.infra.marketplace.coupang;

import com.sellsync.api.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "coupang_display_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CoupangDisplayCategory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "display_category_code", nullable = false, unique = true, length = 20)
    private String displayCategoryCode;

    @Column(name = "display_category_name", nullable = false, length = 200)
    private String displayCategoryName;

    @Column(name = "parent_category_code", length = 20)
    private String parentCategoryCode;

    @Column(name = "depth", nullable = false)
    private Integer depth;
}
