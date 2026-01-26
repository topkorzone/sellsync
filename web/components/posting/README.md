# 필드 매핑 UI 컴포넌트 사용 가이드

## 개요

`FieldMappingSelector` 컴포넌트는 비개발자도 쉽게 전표 필드 매핑을 설정할 수 있도록 만들어진 UI 컴포넌트입니다.

기존에는 `order.buyerName` 같은 복잡한 경로를 직접 입력해야 했지만, 이제는 드롭다운으로 선택할 수 있습니다.

## 주요 기능

### 1. 3단계 선택 프로세스

1. **데이터 출처 선택**: 주문 정보, 상품 정보, 매핑 정보, 시스템 값, 고정값 중 선택
2. **필드 선택**: 선택한 출처에서 사용 가능한 필드를 카테고리별로 표시
3. **집계 방식 선택** (상품 정보인 경우만): 첫 번째, 합계, 연결, 여러 줄 중 선택

### 2. 사용자 친화적 UI

- 각 필드의 설명과 예시 값 표시
- 필드 타입 표시 (텍스트, 숫자, 날짜 등)
- 카테고리별 그룹핑으로 찾기 쉬움
- 현재 선택된 매핑 정보를 한눈에 확인

## 사용 예시

### 기본 사용법

\`\`\`tsx
import { FieldMappingSelector } from '@/components/posting/FieldMappingSelector';
import { useState, useEffect } from 'react';
import { templatesApi } from '@/lib/api/templates';

function TemplateFieldEditor() {
  const [fieldDefinitions, setFieldDefinitions] = useState([]);
  const [mapping, setMapping] = useState({
    sourceType: 'ORDER',
    sourcePath: 'order.buyerName',
    itemAggregation: undefined
  });

  // 필드 정의 로드
  useEffect(() => {
    async function loadDefinitions() {
      const response = await templatesApi.getFieldDefinitions();
      if (response.ok) {
        setFieldDefinitions(response.data);
      }
    }
    loadDefinitions();
  }, []);

  return (
    <div>
      <h2>거래처명 매핑 설정</h2>
      <FieldMappingSelector
        value={mapping}
        onChange={setMapping}
        fieldDefinitions={fieldDefinitions}
      />
    </div>
  );
}
\`\`\`

### 템플릿 필드 추가 시 사용

\`\`\`tsx
function AddTemplateField() {
  const [fieldDefinitions, setFieldDefinitions] = useState([]);
  const [ecountField, setEcountField] = useState('CUST_DES'); // 거래처명
  const [mapping, setMapping] = useState({
    sourceType: 'ORDER',
    sourcePath: '',
  });

  const handleSave = async () => {
    await templatesApi.addField(templateId, {
      ecountFieldCode: ecountField,
      displayOrder: 1,
      sourceType: mapping.sourceType,
      sourcePath: mapping.sourcePath,
      itemAggregation: mapping.itemAggregation,
    });
  };

  return (
    <div className="space-y-4">
      {/* ERP 필드 선택 */}
      <Select value={ecountField} onValueChange={setEcountField}>
        <SelectItem value="CUST_DES">거래처명</SelectItem>
        <SelectItem value="IO_DATE">판매일자</SelectItem>
        {/* ... */}
      </Select>

      {/* 매핑 설정 */}
      <FieldMappingSelector
        value={mapping}
        onChange={setMapping}
        fieldDefinitions={fieldDefinitions}
      />

      <Button onClick={handleSave}>저장</Button>
    </div>
  );
}
\`\`\`

## 선택 가능한 필드 목록

### 주문 정보 (ORDER)

#### 주문자 정보
- 주문자명: `order.buyerName`
- 주문자 연락처: `order.buyerPhone`
- 주문자 ID: `order.buyerId`

#### 수취인 정보
- 수취인명: `order.receiverName`
- 수취인 연락처1: `order.receiverPhone1`
- 수취인 연락처2: `order.receiverPhone2`
- 우편번호: `order.receiverZipCode`
- 배송지 주소: `order.receiverAddress`

#### 금액 정보
- 총 상품금액: `order.totalProductAmount`
- 총 할인금액: `order.totalDiscountAmount`
- 총 배송비: `order.totalShippingAmount`
- 총 결제금액: `order.totalPaymentAmount`

#### 주문 기본 정보
- 마켓 주문번호: `order.marketplaceOrderId`
- 주문일시: `order.orderedAt`
- 결제일시: `order.paidAt`
- 배송 메시지: `order.deliveryMessage`

### 상품 정보 (ORDER_ITEM)

#### 상품 기본 정보
- 상품명: `item.productName`
- 옵션명: `item.optionName`
- 마켓 상품코드: `item.marketplaceProductId`
- 마켓 SKU: `item.marketplaceSku`

#### 수량/금액
- 수량: `item.quantity`
- 단가: `item.unitPrice`
- 라인 금액: `item.lineAmount`
- 할인 금액: `item.discountAmount`

### 상품 매핑 정보 (PRODUCT_MAPPING)

- ERP 품목코드: `mapping.erpProductCode`
- ERP 품목명: `mapping.erpProductName`

### 시스템 값 (SYSTEM)

- 오늘 날짜: `TODAY`
- 현재 시각: `NOW`

### 고정값 (FIXED)

- 고정값 직접 입력

## 집계 방식 (상품 정보만 해당)

- **첫 번째**: 첫 번째 상품의 값만 사용
- **합계**: 모든 상품의 값을 더함 (숫자 필드만)
- **연결**: 모든 상품의 값을 콤마로 연결 (예: "상품A, 상품B")
- **여러 줄**: 각 상품마다 별도의 전표 라인 생성

## 실제 사용 예시

### 예시 1: 거래처명에 주문자명 매핑

```
1. 데이터 출처: 주문 정보
2. 필드 선택: 주문자명
결과: order.buyerName
```

### 예시 2: 품목명에 상품명 매핑 (여러 상품 연결)

```
1. 데이터 출처: 상품 정보
2. 필드 선택: 상품명
3. 집계 방식: 연결
결과: item.productName [CONCAT]
→ "반팔 티셔츠, 청바지, 양말"
```

### 예시 3: 출하창고에 고정값 설정

```
1. 데이터 출처: 고정값
2. 고정값 입력: "00001"
결과: 고정값 "00001"
```

### 예시 4: 판매일자에 오늘 날짜 사용

```
1. 데이터 출처: 시스템 값
2. 필드 선택: 오늘 날짜
결과: TODAY
→ "20260115"
```

## 개발자 확인용

컴포넌트 하단에 현재 매핑 정보가 개발자 친화적 형식으로 표시됩니다:

```
ORDER.order.buyerName
ORDER_ITEM.item.quantity [SUM]
FIXED."00001"
SYSTEM.TODAY
```

## API 연동

백엔드 API: `GET /api/posting-templates/field-definitions`

응답 형식:
```json
{
  "ok": true,
  "data": [
    {
      "sourceType": "ORDER",
      "sourceTypeName": "주문 정보",
      "categories": [
        {
          "categoryName": "주문자 정보",
          "categoryDescription": "주문한 고객의 정보",
          "fields": [
            {
              "fieldPath": "order.buyerName",
              "fieldName": "주문자명",
              "fieldType": "TEXT",
              "category": "주문자 정보",
              "description": "주문한 고객의 이름",
              "exampleValue": "홍길동"
            }
          ]
        }
      ]
    }
  ]
}
```

## 주의사항

1. 필드 정의는 페이지 로드 시 한 번만 가져오면 됩니다.
2. 상품 정보 필드는 반드시 집계 방식을 선택해야 합니다.
3. 고정값은 모든 전표에 동일하게 들어가므로 신중히 사용하세요.
4. 시스템 값은 전표 생성 시점의 값을 사용합니다.

## 확장 가능성

향후 추가될 수 있는 기능:
- 변환 규칙 설정 (날짜 포맷, 문자열 변환 등)
- 조건부 매핑 (IF-THEN-ELSE)
- 계산식 입력 (수량 * 단가 등)
- 매핑 템플릿 저장/불러오기
