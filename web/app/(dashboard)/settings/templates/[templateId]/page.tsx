'use client';

import { useState, useEffect } from 'react';
import { useRouter, useParams } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { ArrowLeft, Plus, Trash2, Save } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { FieldMappingSelector, type FieldMappingValue } from '@/components/posting/FieldMappingSelector';
import { templatesApi, AddFieldRequest, UpdateMappingRequest } from '@/lib/api';
import { formatDate } from '@/lib/utils';
import type { PostingTemplateField } from '@/types';

export default function TemplateDetailPage() {
  const router = useRouter();
  const params = useParams();
  const queryClient = useQueryClient();
  
  const templateId = params?.templateId as string;

  const [isAddFieldOpen, setIsAddFieldOpen] = useState(false);
  const [isMappingOpen, setIsMappingOpen] = useState(false);
  const [selectedField, setSelectedField] = useState<PostingTemplateField | null>(null);
  const [fieldDefinitions, setFieldDefinitions] = useState<any[]>([]);
  const [ecountFields, setEcountFields] = useState<any[]>([]);
  
  const [fieldForm, setFieldForm] = useState<AddFieldRequest>({
    ecountFieldCode: 'IO_DATE',
    displayOrder: 1,
    isRequired: false,
    defaultValue: '',
  });

  const [mappingValue, setMappingValue] = useState<FieldMappingValue>({
    sourceType: 'ORDER',
    sourcePath: '',
  });

  // 필드 정의 및 이카운트 필드 로드
  useEffect(() => {
    async function loadData() {
      try {
        // 필드 정의 로드 (비개발자용 드롭다운 데이터)
        const fieldDefsResponse = await templatesApi.getFieldDefinitions();
        if (fieldDefsResponse.ok && fieldDefsResponse.data) {
          setFieldDefinitions(fieldDefsResponse.data);
        }
        
        // 이카운트 필드 목록 로드 (모든 필드)
        const ecountFieldsResponse = await templatesApi.getECountFields();
        if (ecountFieldsResponse.ok && ecountFieldsResponse.data) {
          setEcountFields(ecountFieldsResponse.data);
        }
      } catch (error) {
        console.error('데이터 로드 실패:', error);
      }
    }
    loadData();
  }, []);

  // 템플릿 상세 조회
  const { data, isLoading } = useQuery({
    queryKey: ['template', templateId],
    queryFn: () => templatesApi.getDetail(templateId),
    enabled: !!templateId, // templateId가 있을 때만 실행
  });

  // 필드 추가
  const addFieldMutation = useMutation({
    mutationFn: (request: AddFieldRequest) => templatesApi.addField(templateId, request),
    onSuccess: () => {
      toast.success('필드가 추가되었습니다');
      setIsAddFieldOpen(false);
      queryClient.invalidateQueries({ queryKey: ['template', templateId] });
    },
    onError: () => {
      toast.error('필드 추가에 실패했습니다');
    },
  });

  // 필드 삭제
  const deleteFieldMutation = useMutation({
    mutationFn: (fieldId: string) => templatesApi.deleteField(templateId, fieldId),
    onSuccess: () => {
      toast.success('필드가 삭제되었습니다');
      queryClient.invalidateQueries({ queryKey: ['template', templateId] });
    },
    onError: () => {
      toast.error('필드 삭제에 실패했습니다');
    },
  });

  // 매핑 업데이트
  const updateMappingMutation = useMutation({
    mutationFn: ({ fieldId, request }: { fieldId: string; request: UpdateMappingRequest }) =>
      templatesApi.updateMapping(fieldId, request),
    onSuccess: () => {
      toast.success('매핑이 설정되었습니다');
      setIsMappingOpen(false);
      queryClient.invalidateQueries({ queryKey: ['template', templateId] });
    },
    onError: () => {
      toast.error('매핑 설정에 실패했습니다');
    },
  });

  const template = data?.data;
  const fields = template?.fields || [];

  const handleAddField = () => {
    if (!fieldForm.ecountFieldCode) {
      toast.error('필드를 선택해주세요');
      return;
    }
    addFieldMutation.mutate(fieldForm);
  };

  const handleDeleteField = (field: PostingTemplateField) => {
    if (confirm(`"${field.fieldNameKr}" 필드를 삭제하시겠습니까?`)) {
      deleteFieldMutation.mutate(field.fieldId);
    }
  };

  const handleOpenMapping = (field: PostingTemplateField) => {
    setSelectedField(field);
    if (field.mapping) {
      setMappingValue({
        sourceType: field.mapping.sourceType as any,
        sourcePath: field.mapping.sourcePath,
        itemAggregation: field.mapping.itemAggregation as any,
      });
    } else {
      setMappingValue({
        sourceType: 'ORDER',
        sourcePath: '',
      });
    }
    setIsMappingOpen(true);
  };

  const handleUpdateMapping = () => {
    if (!selectedField) return;
    if (!mappingValue.sourcePath) {
      toast.error('필드를 선택해주세요');
      return;
    }
    
    // FieldMappingValue를 UpdateMappingRequest 형식으로 변환
    const request: UpdateMappingRequest = {
      sourceType: mappingValue.sourceType,
      sourcePath: mappingValue.sourcePath,
      itemAggregation: mappingValue.itemAggregation,
      transformRule: mappingValue.transformRule,
    };
    
    updateMappingMutation.mutate({
      fieldId: selectedField.fieldId,
      request,
    });
  };

  if (!templateId || isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-muted-foreground">로딩 중...</div>
      </div>
    );
  }

  if (!template) {
    return (
      <div className="flex items-center justify-center min-h-[400px]">
        <div className="text-muted-foreground">템플릿을 찾을 수 없습니다</div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* 헤더 */}
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="sm" onClick={() => router.push('/settings/templates')}>
          <ArrowLeft className="h-4 w-4 mr-2" />
          목록으로
        </Button>
        <div className="flex-1">
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold">{template.templateName}</h1>
            {template.isActive ? (
              <Badge variant="default" className="bg-green-600">활성</Badge>
            ) : (
              <Badge variant="outline">비활성</Badge>
            )}
          </div>
          <p className="text-sm text-muted-foreground mt-1">
            {template.description || '설명 없음'} · ERP: {template.erpCode}
          </p>
        </div>
      </div>

      {/* 템플릿 정보 카드 */}
      <Card>
        <CardHeader>
          <CardTitle>템플릿 정보</CardTitle>
          <CardDescription>전표 템플릿의 기본 정보입니다</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <div className="text-sm font-medium text-muted-foreground">템플릿 ID</div>
              <div className="text-sm font-mono mt-1">{template.templateId}</div>
            </div>
            <div>
              <div className="text-sm font-medium text-muted-foreground">ERP</div>
              <div className="text-sm mt-1">{template.erpCode}</div>
            </div>
            <div>
              <div className="text-sm font-medium text-muted-foreground">전표 타입</div>
              <div className="text-sm mt-1">{template.postingType}</div>
            </div>
            <div>
              <div className="text-sm font-medium text-muted-foreground">상태</div>
              <div className="text-sm mt-1">{template.isActive ? '활성' : '비활성'}</div>
            </div>
            <div>
              <div className="text-sm font-medium text-muted-foreground">생성일</div>
              <div className="text-sm mt-1">{formatDate(template.createdAt)}</div>
            </div>
            <div>
              <div className="text-sm font-medium text-muted-foreground">수정일</div>
              <div className="text-sm mt-1">{formatDate(template.updatedAt)}</div>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* 필드 목록 */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle>전표 필드 ({fields.length}개)</CardTitle>
              <CardDescription>전표에 포함될 필드를 관리합니다</CardDescription>
            </div>
            <Button onClick={() => setIsAddFieldOpen(true)}>
              <Plus className="h-4 w-4 mr-2" />
              필드 추가
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          {fields.length === 0 ? (
            <div className="text-center py-10 text-muted-foreground">
              필드가 없습니다. 필드를 추가해주세요.
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-12">순서</TableHead>
                  <TableHead>필드명</TableHead>
                  <TableHead>코드</TableHead>
                  <TableHead>필수</TableHead>
                  <TableHead>기본값</TableHead>
                  <TableHead>매핑 정보</TableHead>
                  <TableHead className="text-right">작업</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {fields
                  .sort((a, b) => a.displayOrder - b.displayOrder)
                  .map((field) => (
                    <TableRow key={field.fieldId}>
                      <TableCell className="font-mono text-sm">{field.displayOrder}</TableCell>
                      <TableCell className="font-medium">{field.fieldNameKr}</TableCell>
                      <TableCell className="font-mono text-sm">{field.fieldCode}</TableCell>
                      <TableCell>
                        {field.isRequired ? (
                          <Badge variant="destructive" className="text-xs">필수</Badge>
                        ) : (
                          <Badge variant="outline" className="text-xs">선택</Badge>
                        )}
                      </TableCell>
                      <TableCell className="max-w-xs truncate text-sm">
                        {field.defaultValue || '-'}
                      </TableCell>
                      <TableCell>
                        {field.mapping ? (
                          <div className="text-sm">
                            <div className="font-medium">{field.mapping.sourceType}</div>
                            <div className="text-muted-foreground text-xs truncate max-w-xs">
                              {field.mapping.sourcePath}
                            </div>
                          </div>
                        ) : (
                          <span className="text-sm text-muted-foreground">매핑 안 됨</span>
                        )}
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-2">
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleOpenMapping(field)}
                          >
                            매핑 설정
                          </Button>
                          <Button
                            variant="destructive"
                            size="sm"
                            onClick={() => handleDeleteField(field)}
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {/* 필드 추가 다이얼로그 */}
      <Dialog open={isAddFieldOpen} onOpenChange={setIsAddFieldOpen}>
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>필드 추가</DialogTitle>
            <DialogDescription>
              전표에 추가할 이카운트 필드를 선택하세요 (총 {ecountFields.length}개 필드)
            </DialogDescription>
          </DialogHeader>
          
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label>이카운트 필드 *</Label>
              <Select
                value={fieldForm.ecountFieldCode}
                onValueChange={(v) => setFieldForm({ ...fieldForm, ecountFieldCode: v })}
              >
                <SelectTrigger>
                  <SelectValue placeholder="필드를 선택하세요" />
                </SelectTrigger>
                <SelectContent className="max-h-[400px]">
                  {/* 헤더 레벨 필드 */}
                  <div className="px-2 py-1.5 text-sm font-semibold text-muted-foreground bg-muted">
                    📋 전표 헤더 필드 (주문 단위)
                  </div>
                  {ecountFields
                    .filter(field => field.fieldLevel === 'HEADER')
                    .sort((a, b) => {
                      // 필수 필드 먼저
                      if (a.required && !b.required) return -1;
                      if (!a.required && b.required) return 1;
                      return a.fieldNameKr.localeCompare(b.fieldNameKr);
                    })
                    .map((field) => (
                      <SelectItem key={field.fieldCode} value={field.fieldCode}>
                        <div className="flex items-center gap-2 py-1">
                          <span className="font-medium">{field.fieldNameKr}</span>
                          <span className="text-xs text-muted-foreground">({field.fieldCode})</span>
                          {field.required && (
                            <Badge variant="destructive" className="text-xs">필수</Badge>
                          )}
                          <Badge variant="outline" className="text-xs">
                            {field.fieldType}
                          </Badge>
                        </div>
                      </SelectItem>
                    ))}
                  
                  {/* 라인 레벨 필드 */}
                  <div className="px-2 py-1.5 text-sm font-semibold text-muted-foreground bg-muted mt-2">
                    📦 상품 라인 필드 (상품 단위)
                  </div>
                  {ecountFields
                    .filter(field => field.fieldLevel === 'LINE')
                    .sort((a, b) => {
                      // 필수 필드 먼저
                      if (a.required && !b.required) return -1;
                      if (!a.required && b.required) return 1;
                      return a.fieldNameKr.localeCompare(b.fieldNameKr);
                    })
                    .map((field) => (
                      <SelectItem key={field.fieldCode} value={field.fieldCode}>
                        <div className="flex items-center gap-2 py-1">
                          <span className="font-medium">{field.fieldNameKr}</span>
                          <span className="text-xs text-muted-foreground">({field.fieldCode})</span>
                          {field.required && (
                            <Badge variant="destructive" className="text-xs">필수</Badge>
                          )}
                          <Badge variant="outline" className="text-xs">
                            {field.fieldType}
                          </Badge>
                        </div>
                      </SelectItem>
                    ))}
                </SelectContent>
              </Select>
              
              {/* 선택된 필드 정보 표시 */}
              {fieldForm.ecountFieldCode && ecountFields.length > 0 && (
                <div className="rounded-md bg-muted p-3 space-y-2 mt-2">
                  {(() => {
                    const selectedEcountField = ecountFields.find(
                      f => f.fieldCode === fieldForm.ecountFieldCode
                    );
                    if (!selectedEcountField) return null;
                    
                    return (
                      <>
                        <div className="flex items-center gap-2">
                          <span className="font-semibold">{selectedEcountField.fieldNameKr}</span>
                          <Badge variant="outline">{selectedEcountField.fieldCode}</Badge>
                          {selectedEcountField.required && (
                            <Badge variant="destructive" className="text-xs">필수</Badge>
                          )}
                        </div>
                        <p className="text-sm text-muted-foreground">
                          {selectedEcountField.description}
                        </p>
                        {selectedEcountField.exampleValue && (
                          <p className="text-xs">
                            <span className="font-medium">예시:</span>{' '}
                            <code className="bg-background px-1 py-0.5 rounded">
                              {selectedEcountField.exampleValue}
                            </code>
                          </p>
                        )}
                      </>
                    );
                  })()}
                </div>
              )}
            </div>

            <div className="space-y-2">
              <Label>표시 순서</Label>
              <Input
                type="number"
                value={fieldForm.displayOrder}
                onChange={(e) => setFieldForm({ ...fieldForm, displayOrder: parseInt(e.target.value) })}
              />
            </div>

            <div className="space-y-2">
              <Label>기본값 (선택)</Label>
              <Input
                placeholder="값이 없을 때 사용할 기본값"
                value={fieldForm.defaultValue}
                onChange={(e) => setFieldForm({ ...fieldForm, defaultValue: e.target.value })}
              />
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setIsAddFieldOpen(false)}>
              취소
            </Button>
            <Button onClick={handleAddField} disabled={addFieldMutation.isPending}>
              {addFieldMutation.isPending ? '추가 중...' : '추가'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 매핑 설정 다이얼로그 - 비개발자용 UI */}
      <Dialog open={isMappingOpen} onOpenChange={setIsMappingOpen}>
        <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>필드 매핑 설정</DialogTitle>
            <DialogDescription>
              <span className="font-semibold">{selectedField?.fieldNameKr}</span> 
              <span className="text-muted-foreground"> ({selectedField?.fieldCode})</span>
              <br />
              드롭다운에서 선택하여 쉽게 매핑하세요
            </DialogDescription>
          </DialogHeader>
          
          <div className="py-4">
            <FieldMappingSelector
              value={mappingValue}
              onChange={setMappingValue}
              fieldDefinitions={fieldDefinitions}
            />
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setIsMappingOpen(false)}>
              취소
            </Button>
            <Button onClick={handleUpdateMapping} disabled={updateMappingMutation.isPending}>
              <Save className="h-4 w-4 mr-2" />
              {updateMappingMutation.isPending ? '저장 중...' : '저장'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
