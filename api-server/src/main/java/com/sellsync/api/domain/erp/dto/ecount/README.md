# 이카운트 전표 DTO 패키지

## 개요

이카운트 판매 전표 API와 통신하기 위한 DTO 클래스들입니다.

## 클래스 구조

### 1. EcountBulkData
- **목적**: 전표의 개별 라인(상품 라인) 데이터
- **위치**: `com.sellsync.api.domain.erp.dto.ecount.EcountBulkData`
- **주요 필드**:
  - `uploadSerNo`: 전표 그룹 구분용 일련번호
  - `prodCd`: 품목 코드
  - `qty`: 수량
  - `supplyAmt`: 공급가액
  - `vatAmt`: 부가세
  - `ioDate`: 입출고 일자
  - `whCd`: 창고 코드
  - `cust`: 거래처 코드

### 2. EcountSaleItem
- **목적**: BulkData를 감싸는 래퍼 객체
- **위치**: `com.sellsync.api.domain.erp.dto.ecount.EcountSaleItem`
- **구조**: `{ "BulkDatas": { ... } }`
- **팩토리 메서드**: `of(EcountBulkData data)`

### 3. EcountSaleRequest
- **목적**: 이카운트 API 최상위 요청 객체
- **위치**: `com.sellsync.api.domain.erp.dto.ecount.EcountSaleRequest`
- **구조**: `{ "SaleList": [ { "BulkDatas": {...} }, ... ] }`
- **팩토리 메서드**: `of(List<EcountSaleItem> items)`

## JSON 구조

```json
{
  "SaleList": [
    {
      "BulkDatas": {
        "UPLOAD_SER_NO": 1,
        "PROD_CD": "A001",
        "PROD_DES": "상품명",
        "QTY": 2,
        "UNIT": "EA",
        "USER_PRICE_VAT": 10000,
        "SUPPLY_AMT": 18182,
        "VAT_AMT": 1818,
        "P_AMT1": 2000,
        "REMARKS": "비고",
        "P_REMARKS1": "주문번호: ORD-001",
        "IO_DATE": "2026-01-18",
        "WH_CD": "W001",
        "CUST": "C001"
      }
    },
    {
      "BulkDatas": {
        "UPLOAD_SER_NO": 1,
        "PROD_CD": "A002",
        ...
      }
    }
  ]
}
```

## 사용 예시

```java
// 1. BulkData 생성
EcountBulkData bulkData1 = EcountBulkData.builder()
    .uploadSerNo(1)
    .prodCd("A001")
    .prodDes("상품명")
    .qty(2)
    .unit("EA")
    .userPriceVat(10000)
    .supplyAmt(18182)
    .vatAmt(1818)
    .ioDate("2026-01-18")
    .whCd("W001")
    .cust("C001")
    .build();

EcountBulkData bulkData2 = EcountBulkData.builder()
    .uploadSerNo(1)  // 같은 전표
    .prodCd("A002")
    // ...
    .build();

// 2. SaleItem 리스트 생성
List<EcountSaleItem> items = List.of(
    EcountSaleItem.of(bulkData1),
    EcountSaleItem.of(bulkData2)
);

// 3. 최종 Request 생성
EcountSaleRequest request = EcountSaleRequest.of(items);

// 4. API 호출 (예시)
String json = objectMapper.writeValueAsString(request);
// POST 요청...
```

## 주요 특징

### 1. Jackson 어노테이션 사용
- `@JsonProperty`: 이카운트 API의 대문자 필드명과 매핑
- Java에서는 camelCase 사용, JSON 직렬화 시 이카운트 형식으로 변환

### 2. Lombok 활용
- `@Data`: getter/setter/toString/equals/hashCode 자동 생성
- `@Builder`: 빌더 패턴 지원
- `@NoArgsConstructor`: Jackson 역직렬화를 위한 기본 생성자
- `@AllArgsConstructor`: 빌더에서 사용하는 전체 생성자

### 3. 팩토리 메서드 패턴
- `EcountSaleItem.of()`: 간결한 객체 생성
- `EcountSaleRequest.of()`: 리스트를 감싸는 편의 메서드

## 필드 매핑 정보

| Java 필드 | JSON 필드 | 설명 | 필수 여부 |
|----------|-----------|------|----------|
| uploadSerNo | UPLOAD_SER_NO | 전표 그룹 번호 | 필수 |
| prodCd | PROD_CD | 품목 코드 | 필수 |
| prodDes | PROD_DES | 품목 설명 | 선택 |
| qty | QTY | 수량 | 필수 |
| unit | UNIT | 단위 | 선택 |
| userPriceVat | USER_PRICE_VAT | VAT 포함 단가 | 선택 |
| supplyAmt | SUPPLY_AMT | 공급가액 | 필수 |
| vatAmt | VAT_AMT | 부가세 | 선택 |
| pAmt1 | P_AMT1 | 사용자 정의 금액1 | 선택 |
| remarks | REMARKS | 비고 | 선택 |
| pRemarks1 | P_REMARKS1 | 사용자 정의 비고1 | 선택 |
| ioDate | IO_DATE | 입출고 일자 (yyyy-MM-dd) | 필수 |
| whCd | WH_CD | 창고 코드 | 선택 |
| cust | CUST | 거래처 코드 | 필수 |

## 주의사항

1. **날짜 형식**: `ioDate`는 반드시 "yyyy-MM-dd" 형식의 문자열
2. **전표 그룹핑**: 같은 `uploadSerNo`를 가진 라인들은 하나의 전표로 처리됨
3. **금액 계산**: `supplyAmt + vatAmt = 총액` 관계가 성립해야 함
4. **필수 필드**: `uploadSerNo`, `prodCd`, `qty`, `supplyAmt`, `ioDate`, `cust`는 필수

## 다음 단계

Phase 3에서 이 DTO들을 활용하여:
1. Order/Settlement 데이터를 EcountBulkData로 변환하는 Converter 구현
2. 템플릿 기반 필드 매핑 로직 구현
3. 이카운트 API 클라이언트 구현

## 관련 문서

- [Phase 3: 데이터 변환 로직](../converter/README.md) (예정)
- [Phase 4: 이카운트 API 클라이언트](../client/README.md) (예정)
