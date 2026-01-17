package com.sellsync.api.domain.erp.entity;

import com.sellsync.api.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 전표 라인 엔티티
 * - 실제 전표 입력 시 생성되는 라인 데이터
 * - 여러 라인을 체크박스로 선택하여 한 전표로 묶을 수 있음
 */
@Entity
@Table(name = "sale_form_lines", indexes = {
        @Index(name = "idx_sale_line_tenant", columnList = "tenant_id"),
        @Index(name = "idx_sale_line_upload_ser_no", columnList = "tenant_id, upload_ser_no"),
        @Index(name = "idx_sale_line_status", columnList = "tenant_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleFormLine extends BaseEntity {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "upload_ser_no", length = 50)
    private String uploadSerNo;         // 업로드 일련번호 (같은 번호끼리 한 전표로 묶임)

    @Column(name = "io_date", length = 8)
    private String ioDate;              // 판매일자 (YYYYMMDD)

    @Column(name = "cust", length = 50)
    private String cust;                // 거래처코드

    @Column(name = "cust_des", length = 200)
    private String custDes;             // 거래처명

    @Column(name = "emp_cd", length = 50)
    private String empCd;               // 담당자

    @Column(name = "wh_cd", length = 50)
    private String whCd;                // 출하창고

    @Column(name = "io_type", length = 20)
    private String ioType;              // 구분(거래유형)

    @Column(name = "prod_cd", nullable = false, length = 100)
    private String prodCd;              // 품목코드 (필수)

    @Column(name = "prod_des", length = 200)
    private String prodDes;             // 품목명

    @Column(name = "size_des", length = 100)
    private String sizeDes;             // 규격

    @Column(name = "qty", precision = 18, scale = 4)
    private BigDecimal qty;             // 판매수량 (필수)

    @Column(name = "price", precision = 18, scale = 2)
    private BigDecimal price;           // 단가 (필수)

    @Column(name = "supply_amt", precision = 18, scale = 2)
    private BigDecimal supplyAmt;       // 공급가액

    @Column(name = "vat_amt", precision = 18, scale = 2)
    private BigDecimal vatAmt;          // 부가세

    @Column(name = "remarks", length = 500)
    private String remarks;             // 적요

    @Column(name = "site", length = 50)
    private String site;                // 부서

    @Column(name = "pjt_cd", length = 50)
    private String pjtCd;               // 프로젝트

    /**
     * 전체 필드 JSON (API 문서의 모든 필드를 유연하게 저장)
     */
    @Column(name = "form_data", columnDefinition = "TEXT")
    private String formData;

    /**
     * 전표 입력 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SaleFormLineStatus status = SaleFormLineStatus.DRAFT;

    /**
     * 전표 입력 후 생성된 전표번호
     */
    @Column(name = "doc_no", length = 50)
    private String docNo;

    /**
     * ERP 응답 메시지
     */
    @Column(name = "erp_response", columnDefinition = "TEXT")
    private String erpResponse;

    /**
     * 전표 라인 상태
     */
    public enum SaleFormLineStatus {
        DRAFT,      // 임시저장
        PENDING,    // 전표입력 대기
        POSTED,     // 전표입력 완료
        FAILED      // 전표입력 실패
    }

    /**
     * 전표입력 완료 처리
     */
    public void markAsPosted(String docNo, String erpResponse) {
        this.status = SaleFormLineStatus.POSTED;
        this.docNo = docNo;
        this.erpResponse = erpResponse;
    }

    /**
     * 전표입력 실패 처리
     */
    public void markAsFailed(String erpResponse) {
        this.status = SaleFormLineStatus.FAILED;
        this.erpResponse = erpResponse;
    }
}
