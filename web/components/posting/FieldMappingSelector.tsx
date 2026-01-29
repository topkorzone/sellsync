"use client";

import { useState } from 'react';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';

/**
 * 필드 정의 타입
 */
interface FieldDefinition {
  fieldPath: string;
  fieldName: string;
  fieldType: string;
  category: string;
  description: string;
  exampleValue: string;
}

interface FieldCategory {
  categoryName: string;
  categoryDescription: string;
  fields: FieldDefinition[];
}

interface FieldSourceDefinition {
  sourceType: string;
  sourceTypeName: string;
  categories: FieldCategory[];
}

/**
 * 필드 매핑 정보
 */
export interface FieldMappingValue {
  sourceType: 'ORDER' | 'ORDER_ITEM' | 'PRODUCT_MAPPING' | 'ERP_ITEM' | 'FORMULA' | 'SYSTEM' | 'FIXED';
  sourcePath: string;
  itemAggregation?: 'FIRST' | 'SUM' | 'CONCAT' | 'MULTI_LINE';
  transformRule?: string;
}

interface FieldMappingSelectorProps {
  value?: FieldMappingValue;
  onChange: (value: FieldMappingValue) => void;
  fieldDefinitions?: FieldSourceDefinition[];
  disabled?: boolean;
}

/**
 * 필드 매핑 선택기 컴포넌트
 * 
 * 비개발자도 쉽게 필드를 선택할 수 있도록 드롭다운 기반 UI 제공
 */
export function FieldMappingSelector({
  value,
  onChange,
  fieldDefinitions = [],
  disabled = false
}: FieldMappingSelectorProps) {
  const [selectedSource, setSelectedSource] = useState<string>(value?.sourceType || '');
  const [selectedField, setSelectedField] = useState<string>(value?.sourcePath || '');
  const [fixedValue, setFixedValue] = useState<string>(
    (value?.sourceType === 'FIXED' || value?.sourceType === 'FORMULA') ? value.sourcePath : ''
  );
  const [aggregationType, setAggregationType] = useState<string>(value?.itemAggregation || 'FIRST');

  // 선택된 소스의 필드 목록
  const selectedSourceDef = fieldDefinitions.find(s => s.sourceType === selectedSource);
  
  // 선택된 필드의 상세 정보
  const selectedFieldDef = selectedSourceDef?.categories
    .flatMap(c => c.fields)
    .find(f => f.fieldPath === selectedField);

  // 소스 타입 변경
  const handleSourceChange = (newSource: string) => {
    setSelectedSource(newSource);
    setSelectedField('');
    setFixedValue('');
    
    // 소스 타입에 따라 초기 값 설정
    if (newSource === 'FIXED') {
      onChange({
        sourceType: 'FIXED',
        sourcePath: ''
      });
    } else if (newSource === 'FORMULA') {
      onChange({
        sourceType: 'FORMULA',
        sourcePath: ''
      });
    } else if (newSource === 'SYSTEM') {
      // SYSTEM은 아직 처리 안함
      onChange({
        sourceType: 'SYSTEM',
        sourcePath: ''
      });
    } else {
      // ORDER, ORDER_ITEM, PRODUCT_MAPPING, ERP_ITEM
      onChange({
        sourceType: newSource as any,
        sourcePath: '',
        itemAggregation: newSource === 'ORDER_ITEM' ? 'FIRST' : undefined
      });
    }
  };

  // 필드 선택
  const handleFieldChange = (newField: string) => {
    setSelectedField(newField);
    
    onChange({
      sourceType: selectedSource as any,
      sourcePath: newField,
      itemAggregation: selectedSource === 'ORDER_ITEM' ? (aggregationType as any) : undefined
    });
  };

  // 고정값 변경
  const handleFixedValueChange = (newValue: string) => {
    setFixedValue(newValue);
    onChange({
      sourceType: 'FIXED',
      sourcePath: newValue
    });
  };

  // 계산식 변경
  const handleFormulaChange = (newValue: string) => {
    setFixedValue(newValue);
    onChange({
      sourceType: 'FORMULA',
      sourcePath: newValue
    });
  };

  // 집계 방식 변경
  const handleAggregationChange = (newAggregation: string) => {
    setAggregationType(newAggregation);
    
    if (selectedField) {
      onChange({
        sourceType: selectedSource as any,
        sourcePath: selectedField,
        itemAggregation: newAggregation as any
      });
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">필드 매핑 설정</CardTitle>
        <CardDescription className="text-xs">
          주문/상품 정보에서 가져올 데이터를 선택하세요
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* 1단계: 데이터 소스 선택 */}
        <div className="space-y-2">
          <Label>1. 데이터 출처</Label>
          <Select value={selectedSource} onValueChange={handleSourceChange} disabled={disabled}>
            <SelectTrigger>
              <SelectValue placeholder="데이터를 가져올 위치를 선택하세요" />
            </SelectTrigger>
            <SelectContent>
              {fieldDefinitions.map((source) => (
                <SelectItem key={source.sourceType} value={source.sourceType}>
                  {source.sourceTypeName}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {/* 2단계: 고정값 입력, 계산식 입력, 또는 필드 선택 */}
        {selectedSource === 'FIXED' ? (
          <div className="space-y-2">
            <Label>2. 고정값 입력</Label>
            <Input
              value={fixedValue}
              onChange={(e) => handleFixedValueChange(e.target.value)}
              placeholder="모든 전표에 동일하게 들어갈 값을 입력하세요"
              disabled={disabled}
            />
            <p className="text-xs text-muted-foreground">
              예: 창고코드 &quot;00001&quot;, 거래처코드 &quot;CUST001&quot; 등
            </p>
          </div>
        ) : selectedSource === 'FORMULA' ? (
          <div className="space-y-2">
            <Label>2. 계산식 입력</Label>
            <Input
              value={fixedValue}
              onChange={(e) => handleFormulaChange(e.target.value)}
              placeholder="예: order.marketplaceOrderId + ' ' + order.buyerName"
              disabled={disabled}
              className="font-mono"
            />
            <div className="rounded-md bg-muted p-3 space-y-2 text-xs">
              <p className="font-medium">📐 계산식 작성 방법</p>
              <ul className="space-y-1 list-disc list-inside text-muted-foreground">
                <li><strong>숫자 계산:</strong> <code className="bg-background px-1 rounded">+</code>{' '}
                  <code className="bg-background px-1 rounded">-</code>{' '}
                  <code className="bg-background px-1 rounded">*</code>{' '}
                  <code className="bg-background px-1 rounded">/</code>{' '}
                  <code className="bg-background px-1 rounded">()</code></li>
                <li><strong>문자열 연결:</strong> <code className="bg-background px-1 rounded">+</code> 연산자 사용</li>
                <li><strong>문자열 리터럴:</strong> 작은따옴표로 감싸기 <code className="bg-background px-1 rounded">&apos;텍스트&apos;</code></li>
                <li><strong>필드 참조:</strong> <code className="bg-background px-1 rounded">order.필드명</code>, <code className="bg-background px-1 rounded">item.필드명</code></li>
              </ul>
              <p className="font-medium mt-2">💡 숫자 계산 예시</p>
              <ul className="space-y-1 list-disc list-inside text-muted-foreground">
                <li><code className="bg-background px-1 rounded">order.totalPaymentAmount / item.quantity</code> - 개당 단가</li>
                <li><code className="bg-background px-1 rounded">item.unitPrice * item.quantity</code> - 라인 금액</li>
                <li><code className="bg-background px-1 rounded">(order.totalProductAmount - order.totalDiscountAmount) / item.quantity</code> - 할인 적용 단가</li>
              </ul>
              <p className="font-medium mt-2">💡 문자열 연결 예시</p>
              <ul className="space-y-1 list-disc list-inside text-muted-foreground">
                <li><code className="bg-background px-1 rounded">order.marketplaceOrderId + &apos; &apos; + order.buyerName</code> - 주문번호 주문자명</li>
                <li><code className="bg-background px-1 rounded">order.buyerName + &apos;님&apos;</code> - 주문자명님</li>
                <li><code className="bg-background px-1 rounded">&apos;주문번호: &apos; + order.marketplaceOrderId</code> - 주문번호: ORD123</li>
              </ul>
            </div>
          </div>
        ) : selectedSource && selectedSourceDef ? (
          <div className="space-y-2">
            <Label>2. 필드 선택</Label>
            <Select value={selectedField} onValueChange={handleFieldChange} disabled={disabled}>
              <SelectTrigger>
                <SelectValue placeholder="가져올 필드를 선택하세요" />
              </SelectTrigger>
              <SelectContent>
                {selectedSourceDef.categories.map((category) => (
                  <SelectGroup key={category.categoryName}>
                    <SelectLabel>{category.categoryName}</SelectLabel>
                    {category.fields.map((field) => (
                      <SelectItem key={field.fieldPath} value={field.fieldPath}>
                        <div className="flex items-center gap-2">
                          <span>{field.fieldName}</span>
                          <Badge variant="outline" className="text-xs">
                            {field.fieldType}
                          </Badge>
                        </div>
                      </SelectItem>
                    ))}
                  </SelectGroup>
                ))}
              </SelectContent>
            </Select>

            {/* 선택된 필드 정보 표시 */}
            {selectedFieldDef && (
              <div className="rounded-md bg-muted p-3 space-y-1">
                <p className="text-sm font-medium">{selectedFieldDef.fieldName}</p>
                <p className="text-xs text-muted-foreground">
                  {selectedFieldDef.description}
                </p>
                {selectedFieldDef.exampleValue && (
                  <p className="text-xs">
                    <span className="font-medium">예시:</span>{' '}
                    <code className="bg-background px-1 py-0.5 rounded">
                      {selectedFieldDef.exampleValue}
                    </code>
                  </p>
                )}
              </div>
            )}
          </div>
        ) : null}

        {/* 3단계: 상품 정보인 경우 집계 방식 선택 */}
        {selectedSource === 'ORDER_ITEM' && selectedField && (
          <div className="space-y-2">
            <Label>3. 집계 방식</Label>
            <Select value={aggregationType} onValueChange={handleAggregationChange} disabled={disabled}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="FIRST">
                  <div className="space-y-1">
                    <div className="font-medium">첫 번째 상품</div>
                    <div className="text-xs text-muted-foreground">
                      첫 번째 상품의 값만 사용
                    </div>
                  </div>
                </SelectItem>
                <SelectItem value="SUM">
                  <div className="space-y-1">
                    <div className="font-medium">합계</div>
                    <div className="text-xs text-muted-foreground">
                      모든 상품의 값을 더함 (숫자만 가능)
                    </div>
                  </div>
                </SelectItem>
                <SelectItem value="CONCAT">
                  <div className="space-y-1">
                    <div className="font-medium">연결</div>
                    <div className="text-xs text-muted-foreground">
                      모든 상품의 값을 콤마로 연결
                    </div>
                  </div>
                </SelectItem>
                <SelectItem value="MULTI_LINE">
                  <div className="space-y-1">
                    <div className="font-medium">여러 줄</div>
                    <div className="text-xs text-muted-foreground">
                      각 상품마다 별도 라인 생성
                    </div>
                  </div>
                </SelectItem>
              </SelectContent>
            </Select>
          </div>
        )}

        {/* 현재 매핑 정보 표시 (개발자 확인용) */}
        {value && (
          <div className="rounded-md bg-muted/50 p-2 mt-4">
            <p className="text-xs font-mono text-muted-foreground">
              {value.sourceType === 'FIXED' 
                ? `고정값: "${value.sourcePath}"`
                : value.sourceType === 'FORMULA'
                  ? `계산식: "${value.sourcePath}"`
                  : `${value.sourceType}.${value.sourcePath}${
                      value.itemAggregation ? ` [${value.itemAggregation}]` : ''
                    }`
              }
            </p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
