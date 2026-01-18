# SmartStore 일별정산내역 API 응답 DTO

## 파일 구조

```
src/main/java/com/sellsync/api/domain/settlement/dto/smartstore/
├── DailySettlementApiResponse.java    # API 응답 wrapper
└── DailySettlementElement.java        # 개별 정산 내역
```

## API 스펙

- **엔드포인트**: `GET /v1/pay-settle/settle/daily`
- **응답 형식**: JSON

## 응답 구조 예시

```json
{
  "elements": [
    {
      "settleBasisStartDate": "20260115",
      "settleBasisEndDate": "20260115",
      "settleExpectDate": "20260117",
      "settleCompleteDate": "20260117",
      "settleAmount": 50000,
      "commissionAmount": 5000,
      "shippingSettleAmount": 3000,
      "benefitSettleAmount": 0,
      "productOrderCount": 10
    }
  ]
}
```

## 클래스 설명

### DailySettlementApiResponse
- 전체 API 응답을 감싸는 wrapper 클래스
- `elements` 필드에 정산 내역 리스트를 포함

### DailySettlementElement
- 개별 정산 건의 상세 정보를 담는 클래스
- 모든 필드는 SmartStore API 응답 필드명과 동일하게 매핑

#### 주요 필드

| 필드명 | 타입 | 설명 | Nullable |
|--------|------|------|----------|
| settleBasisStartDate | String | 정산기준 시작일 (yyyyMMdd) | No |
| settleBasisEndDate | String | 정산기준 종료일 (yyyyMMdd) | No |
| settleExpectDate | String | 정산 예정일 (yyyyMMdd) | No |
| settleCompleteDate | String | 정산 완료일 (yyyyMMdd) | Yes |
| settleAmount | Long | 정산 금액 (원) | No |
| commissionAmount | Long | 수수료 (원) | No |
| shippingSettleAmount | Long | 배송비 정산 금액 (원) | No |
| benefitSettleAmount | Long | 혜택 정산 금액 (원) | No |
| productOrderCount | Integer | 상품주문 건수 | No |

## 헬퍼 메서드

### 날짜 변환 메서드
- `getSettleBasisStartDateAsLocalDate()`: 정산기준 시작일을 LocalDate로 변환
- `getSettleBasisEndDateAsLocalDate()`: 정산기준 종료일을 LocalDate로 변환
- `getSettleExpectDateAsLocalDate()`: 정산 예정일을 LocalDate로 변환
- `getSettleCompleteDateAsLocalDate()`: 정산 완료일을 LocalDate로 변환 (nullable)

### 계산 메서드
- `getNetSettlementAmount()`: 실제 정산된 순 금액 계산
  - 공식: `정산금액 - 수수료 + 배송비 정산 금액 + 혜택 정산 금액`
- `isSettlementCompleted()`: 정산 완료 여부 확인

## 사용 예시

```java
// Jackson ObjectMapper를 사용한 역직렬화
ObjectMapper mapper = new ObjectMapper();
DailySettlementApiResponse response = mapper.readValue(jsonString, DailySettlementApiResponse.class);

// 정산 내역 순회
for (DailySettlementElement element : response.getElements()) {
    // 날짜 변환
    LocalDate startDate = element.getSettleBasisStartDateAsLocalDate();
    LocalDate endDate = element.getSettleBasisEndDateAsLocalDate();
    
    // 순 정산 금액 계산
    Long netAmount = element.getNetSettlementAmount();
    
    // 정산 완료 여부 확인
    if (element.isSettlementCompleted()) {
        LocalDate completeDate = element.getSettleCompleteDateAsLocalDate();
        System.out.println("정산 완료일: " + completeDate);
    }
    
    System.out.println(String.format(
        "정산기간: %s ~ %s, 정산금액: %d원, 순정산금액: %d원",
        startDate, endDate, element.getSettleAmount(), netAmount
    ));
}
```

## 특징

1. **Lombok 사용**: `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` 어노테이션으로 boilerplate 코드 최소화
2. **날짜 변환**: String 형식의 날짜를 LocalDate로 변환하는 헬퍼 메서드 제공
3. **유틸리티 메서드**: 순 정산 금액 계산, 정산 완료 여부 확인 등의 편의 기능 제공
4. **Jackson 호환**: `@JsonIgnore`를 통해 헬퍼 메서드가 직렬화되지 않도록 처리
5. **Null-safe**: 모든 계산 메서드에서 null 체크 수행

## 주의사항

- 날짜 필드는 SmartStore API 스펙에 따라 `yyyyMMdd` 형식의 String으로 받습니다.
- `settleCompleteDate`는 정산이 완료되지 않은 경우 null일 수 있습니다.
- 금액 필드는 모두 원(KRW) 단위입니다.
