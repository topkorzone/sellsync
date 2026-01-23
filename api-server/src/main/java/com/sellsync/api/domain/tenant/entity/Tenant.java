package com.sellsync.api.domain.tenant.entity;

import com.sellsync.api.domain.common.BaseEntity;
import com.sellsync.api.domain.tenant.enums.OnboardingStatus;
import com.sellsync.api.domain.tenant.enums.TenantStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 테넌트(고객사) 엔티티
 * 
 * <p>각 고객사를 나타내는 엔티티로, 멀티테넌시 구조의 핵심입니다.
 */
@Entity
@Table(name = "tenants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Tenant extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tenant_id", updatable = false, nullable = false)
    private UUID tenantId;
    
    /**
     * 고객사명
     */
    @Column(nullable = false, length = 200)
    private String name;
    
    /**
     * 사업자등록번호
     */
    @Column(name = "biz_no", length = 20)
    private String bizNo;
    
    /**
     * 타임존 설정 (기본값: Asia/Seoul)
     */
    @Column(nullable = false, length = 50)
    @Builder.Default
    private String timezone = "Asia/Seoul";
    
    /**
     * 테넌트 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TenantStatus status = TenantStatus.ACTIVE;
    
    /**
     * 온보딩 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_status", nullable = false, length = 20)
    @Builder.Default
    private OnboardingStatus onboardingStatus = OnboardingStatus.PENDING;
    
    /**
     * 온보딩 완료 시각
     */
    @Column(name = "onboarding_completed_at")
    private LocalDateTime onboardingCompletedAt;
    
    /**
     * 전화번호
     */
    @Column(length = 20)
    private String phone;
    
    /**
     * 주소
     */
    @Column(length = 500)
    private String address;
    
    /**
     * 테넌트 상태 변경
     */
    public void changeStatus(TenantStatus status) {
        this.status = status;
    }
    
    /**
     * 테넌트 정보 수정
     */
    public void updateInfo(String name, String bizNo, String timezone) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        if (bizNo != null && !bizNo.isBlank()) {
            this.bizNo = bizNo;
        }
        if (timezone != null && !timezone.isBlank()) {
            this.timezone = timezone;
        }
    }
    
    /**
     * 온보딩 상태 변경
     */
    public void updateOnboardingStatus(OnboardingStatus status) {
        this.onboardingStatus = status;
        if (status == OnboardingStatus.COMPLETED) {
            this.onboardingCompletedAt = LocalDateTime.now();
        }
    }
    
    /**
     * 온보딩 완료 여부 확인
     */
    public boolean isOnboardingCompleted() {
        return this.onboardingStatus == OnboardingStatus.COMPLETED 
            || this.onboardingStatus == OnboardingStatus.SKIPPED;
    }
    
    /**
     * 사업자 정보 수정
     */
    public void updateBusinessInfo(String bizNo, String phone, String address) {
        if (bizNo != null && !bizNo.isBlank()) {
            this.bizNo = bizNo;
        }
        if (phone != null && !phone.isBlank()) {
            this.phone = phone;
        }
        if (address != null && !address.isBlank()) {
            this.address = address;
        }
    }
}
