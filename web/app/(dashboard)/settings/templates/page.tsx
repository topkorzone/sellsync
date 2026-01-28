'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { templatesApi, CreateTemplateRequest } from '@/lib/api';
import { formatDate } from '@/lib/utils';
import type { PostingTemplate, PostingType } from '@/types';

const POSTING_TYPES: { value: PostingType; label: string }[] = [
  { value: 'PRODUCT_SALES', label: '상품 매출' },
  { value: 'SHIPPING_FEE', label: '배송비' },
  { value: 'PRODUCT_CANCEL', label: '상품 취소' },
  { value: 'SHIPPING_CANCEL', label: '배송비 취소' },
];

export default function TemplatesPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [formData, setFormData] = useState<CreateTemplateRequest>({
    templateName: '',
    erpCode: 'ECOUNT',
    postingType: 'PRODUCT_SALES',
    description: '',
  });

  // 템플릿 목록 조회
  const { data, isLoading } = useQuery({
    queryKey: ['templates'],
    queryFn: () => templatesApi.getList(),
  });

  // 템플릿 삭제
  const deleteMutation = useMutation({
    mutationFn: (templateId: string) => templatesApi.delete(templateId),
    onSuccess: () => {
      toast.success('템플릿이 삭제되었습니다');
      queryClient.invalidateQueries({ queryKey: ['templates'] });
    },
    onError: () => {
      toast.error('템플릿 삭제에 실패했습니다');
    },
  });

  // 템플릿 활성화
  const activateMutation = useMutation({
    mutationFn: (templateId: string) => templatesApi.activate(templateId),
    onSuccess: () => {
      toast.success('템플릿이 활성화되었습니다');
      queryClient.invalidateQueries({ queryKey: ['templates'] });
    },
    onError: () => {
      toast.error('템플릿 활성화에 실패했습니다');
    },
  });

  // 템플릿 비활성화
  const deactivateMutation = useMutation({
    mutationFn: (templateId: string) => templatesApi.deactivate(templateId),
    onSuccess: () => {
      toast.success('템플릿이 비활성화되었습니다');
      queryClient.invalidateQueries({ queryKey: ['templates'] });
    },
    onError: () => {
      toast.error('템플릿 비활성화에 실패했습니다');
    },
  });

  // 템플릿 생성
  const createMutation = useMutation({
    mutationFn: (request: CreateTemplateRequest) => templatesApi.create(request),
    onSuccess: (response) => {
      toast.success('템플릿이 생성되었습니다');
      setIsCreateOpen(false);
      setFormData({
        templateName: '',
        erpCode: 'ECOUNT',
        postingType: 'PRODUCT_SALES',
        description: '',
      });
      queryClient.invalidateQueries({ queryKey: ['templates'] });
      // 상세 페이지로 이동
      if (response.data?.templateId) {
        router.push(`/settings/templates/${response.data.templateId}`);
      }
    },
    onError: () => {
      toast.error('템플릿 생성에 실패했습니다');
    },
  });

  const templates = data?.data || [];

  const handleCreate = () => {
    if (!formData.templateName.trim()) {
      toast.error('템플릿 이름을 입력해주세요');
      return;
    }
    createMutation.mutate(formData);
  };

  const handleDelete = (template: PostingTemplate) => {
    if (template.isActive) {
      toast.error('활성 템플릿은 삭제할 수 없습니다. 먼저 비활성화하세요.');
      return;
    }
    if (confirm(`"${template.templateName}" 템플릿을 삭제하시겠습니까?`)) {
      deleteMutation.mutate(template.templateId);
    }
  };

  const handleActivate = (template: PostingTemplate) => {
    if (confirm(`"${template.templateName}" 템플릿을 활성화하시겠습니까?\n기존 활성 템플릿은 자동으로 비활성화됩니다.`)) {
      activateMutation.mutate(template.templateId);
    }
  };

  const handleDeactivate = (template: PostingTemplate) => {
    if (confirm(`"${template.templateName}" 템플릿을 비활성화하시겠습니까?`)) {
      deactivateMutation.mutate(template.templateId);
    }
  };

  return (
    <div className="space-y-6">
      {/* 헤더 */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">전표 템플릿 관리</h1>
          <p className="text-muted-foreground">전표 양식을 커스터마이징하세요</p>
        </div>
        <Button onClick={() => setIsCreateOpen(true)}>
          + 새 템플릿 만들기
        </Button>
      </div>

      {/* 안내 메시지 */}
      <div className="rounded-lg border border-blue-200 bg-blue-50 p-4">
        <h3 className="font-semibold text-blue-900 mb-1">💡 전표 템플릿이란?</h3>
        <p className="text-sm text-blue-800">
          회사 상황에 맞는 전표 양식을 설정할 수 있습니다. 필요한 필드만 선택하고, 
          각 필드에 주문 데이터를 매핑하면 자동으로 전표가 생성됩니다.
        </p>
      </div>

      {/* 템플릿 테이블 */}
      <div className="rounded-md border bg-white">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>템플릿 이름</TableHead>
              <TableHead>ERP</TableHead>
              <TableHead>전표 타입</TableHead>
              <TableHead>상태</TableHead>
              <TableHead>필드 수</TableHead>
              <TableHead>설명</TableHead>
              <TableHead>생성일</TableHead>
              <TableHead className="text-right">작업</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={8} className="text-center py-10">
                  로딩 중...
                </TableCell>
              </TableRow>
            ) : templates.length === 0 ? (
              <TableRow>
                <TableCell colSpan={8} className="text-center py-10 text-muted-foreground">
                  템플릿이 없습니다. 새 템플릿을 만들어보세요!
                </TableCell>
              </TableRow>
            ) : (
              templates.map((template) => (
                <TableRow
                  key={template.templateId}
                  className="cursor-pointer hover:bg-muted/50"
                  onClick={() => router.push(`/settings/templates/${template.templateId}`)}
                >
                  <TableCell className="font-medium">{template.templateName}</TableCell>
                  <TableCell>{template.erpCode}</TableCell>
                  <TableCell>
                    {POSTING_TYPES.find(t => t.value === template.postingType)?.label || template.postingType}
                  </TableCell>
                  <TableCell>
                    {template.isActive ? (
                      <Badge variant="default" className="bg-green-600">활성</Badge>
                    ) : (
                      <Badge variant="outline">비활성</Badge>
                    )}
                  </TableCell>
                  <TableCell>{template.fields?.length || 0}개</TableCell>
                  <TableCell className="max-w-xs truncate">{template.description || '-'}</TableCell>
                  <TableCell>{formatDate(template.createdAt)}</TableCell>
                  <TableCell className="text-right" onClick={(e) => e.stopPropagation()}>
                    <div className="flex justify-end gap-2">
                      {template.isActive ? (
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleDeactivate(template)}
                          disabled={deactivateMutation.isPending}
                        >
                          비활성화
                        </Button>
                      ) : (
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleActivate(template)}
                          disabled={activateMutation.isPending}
                        >
                          활성화
                        </Button>
                      )}
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => router.push(`/settings/templates/${template.templateId}`)}
                      >
                        편집
                      </Button>
                      <Button
                        variant="destructive"
                        size="sm"
                        onClick={() => handleDelete(template)}
                        disabled={template.isActive || deleteMutation.isPending}
                      >
                        삭제
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {/* 생성 다이얼로그 */}
      <Dialog open={isCreateOpen} onOpenChange={setIsCreateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>새 전표 템플릿 만들기</DialogTitle>
            <DialogDescription>
              전표 템플릿의 기본 정보를 입력하세요. 생성 후 필드를 추가할 수 있습니다.
            </DialogDescription>
          </DialogHeader>
          
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="templateName">템플릿 이름 *</Label>
              <Input
                id="templateName"
                placeholder="예: 우리 회사 매출전표"
                value={formData.templateName}
                onChange={(e) => setFormData({ ...formData, templateName: e.target.value })}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="erpCode">ERP *</Label>
              <Select
                value={formData.erpCode}
                onValueChange={(v) => setFormData({ ...formData, erpCode: v })}
              >
                <SelectTrigger id="erpCode">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ECOUNT">이카운트</SelectItem>
                  <SelectItem value="SAP" disabled>SAP (준비 중)</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="postingType">전표 타입 *</Label>
              <Select
                value={formData.postingType}
                onValueChange={(v) => setFormData({ ...formData, postingType: v as PostingType })}
              >
                <SelectTrigger id="postingType">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {POSTING_TYPES.map((type) => (
                    <SelectItem key={type.value} value={type.value}>
                      {type.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="description">설명 (선택)</Label>
              <Input
                id="description"
                placeholder="템플릿 설명을 입력하세요"
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              />
            </div>
          </div>

          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setIsCreateOpen(false)}
              disabled={createMutation.isPending}
            >
              취소
            </Button>
            <Button
              onClick={handleCreate}
              disabled={createMutation.isPending}
            >
              {createMutation.isPending ? '생성 중...' : '생성'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
