'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { 
  Plus, 
  Trash2, 
  Edit, 
  Send, 
  FileText,
  CheckSquare,
  Square
} from 'lucide-react';

import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { PageHeader } from '@/components/layout/page-header';
import { Loading } from '@/components/common/loading';
import { EmptyState } from '@/components/common/empty-state';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';

import type { 
  SaleFormTemplate,
  SaleFormLine,
  CreateSaleFormLineRequest,
  CreateSaleFormTemplateRequest
} from '@/types';
import { saleFormApi } from '@/lib/api/sale-forms';

export default function SaleFormsPage() {
  const queryClient = useQueryClient();
  const [selectedLines, setSelectedLines] = useState<Set<string>>(new Set());
  const [showTemplateDialog, setShowTemplateDialog] = useState(false);
  const [showLineDialog, setShowLineDialog] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState<SaleFormTemplate | null>(null);
  const [editingLine, setEditingLine] = useState<SaleFormLine | null>(null);

  // 템플릿 폼 상태
  const [templateForm, setTemplateForm] = useState<CreateSaleFormTemplateRequest>({
    templateName: '',
    isDefault: false,
    description: '',
    defaultCustomerCode: '',
    defaultWarehouseCode: '00009',
    defaultIoType: '',
    defaultEmpCd: '',
    defaultSite: '',
  });

  // 라인 폼 상태
  const [lineForm, setLineForm] = useState<CreateSaleFormLineRequest>({
    ioDate: new Date().toISOString().split('T')[0].replace(/-/g, ''),
    cust: '',
    custDes: '',
    empCd: '',
    whCd: '00009',
    ioType: '',
    prodCd: '',
    prodDes: '',
    sizeDes: '',
    qty: 1,
    price: 0,
    supplyAmt: 0,
    vatAmt: 0,
    remarks: '',
    site: '',
    pjtCd: '',
  });

  // 데이터 로드 (useQuery)
  const { data: templates = [], isLoading: templatesLoading } = useQuery({
    queryKey: ['sale-form-templates'],
    queryFn: () => saleFormApi.getSaleFormTemplates(),
  });

  const { data: linesData, isLoading: linesLoading } = useQuery({
    queryKey: ['sale-form-lines'],
    queryFn: () => saleFormApi.getSaleFormLines({ page: 0, size: 100 }),
  });

  const lines = linesData?.items || [];
  const loading = templatesLoading || linesLoading;

  // Mutations
  const saveTemplateMutation = useMutation({
    mutationFn: (data: { id?: string; form: CreateSaleFormTemplateRequest }) =>
      data.id
        ? saleFormApi.updateSaleFormTemplate(data.id, data.form)
        : saleFormApi.createSaleFormTemplate(data.form),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sale-form-templates'] });
      toast.success(editingTemplate ? '템플릿이 수정되었습니다' : '템플릿이 생성되었습니다');
      setShowTemplateDialog(false);
      setEditingTemplate(null);
    },
    onError: (error: any) => {
      toast.error('템플릿 저장 실패', { description: error.message });
    },
  });

  const deleteTemplateMutation = useMutation({
    mutationFn: (templateId: string) => saleFormApi.deleteSaleFormTemplate(templateId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sale-form-templates'] });
      toast.success('템플릿이 삭제되었습니다');
    },
    onError: (error: any) => {
      toast.error('템플릿 삭제 실패', { description: error.message });
    },
  });

  const saveLineMutation = useMutation({
    mutationFn: (data: { id?: string; form: CreateSaleFormLineRequest }) =>
      data.id
        ? saleFormApi.updateSaleFormLine(data.id, data.form)
        : saleFormApi.createSaleFormLine(data.form),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sale-form-lines'] });
      toast.success(editingLine ? '라인이 수정되었습니다' : '라인이 생성되었습니다');
      setShowLineDialog(false);
      setEditingLine(null);
    },
    onError: (error: any) => {
      toast.error('라인 저장 실패', { description: error.message });
    },
  });

  const deleteLineMutation = useMutation({
    mutationFn: (lineId: string) => saleFormApi.deleteSaleFormLine(lineId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sale-form-lines'] });
      toast.success('라인이 삭제되었습니다');
    },
    onError: (error: any) => {
      toast.error('라인 삭제 실패', { description: error.message });
    },
  });

  const postSaleFormsMutation = useMutation({
    mutationFn: (mergeToSingleDocument: boolean) =>
      saleFormApi.postSaleForms({
        lineIds: Array.from(selectedLines),
        mergeToSingleDocument,
      }),
    onSuccess: (result) => {
      if (result.success) {
        toast.success('전표가 입력되었습니다', {
          description: `전표번호: ${result.documentNo}`,
        });
        setSelectedLines(new Set());
        queryClient.invalidateQueries({ queryKey: ['sale-form-lines'] });
      } else {
        toast.error('전표 입력 실패', { description: result.errorMessage });
      }
    },
    onError: (error: any) => {
      toast.error('전표 입력 실패', { description: error.message });
    },
  });

  // 핸들러
  const handleSaveTemplate = () => {
    saveTemplateMutation.mutate({
      id: editingTemplate?.id,
      form: templateForm,
    });
  };

  const handleDeleteTemplate = (templateId: string) => {
    if (confirm('정말 삭제하시겠습니까?')) {
      deleteTemplateMutation.mutate(templateId);
    }
  };

  const handleSaveLine = () => {
    saveLineMutation.mutate({
      id: editingLine?.id,
      form: lineForm,
    });
  };

  const handleDeleteLine = (lineId: string) => {
    if (confirm('정말 삭제하시겠습니까?')) {
      deleteLineMutation.mutate(lineId);
    }
  };

  // 체크박스 토글
  const toggleLineSelection = (lineId: string) => {
    const newSelection = new Set(selectedLines);
    if (newSelection.has(lineId)) {
      newSelection.delete(lineId);
    } else {
      newSelection.add(lineId);
    }
    setSelectedLines(newSelection);
  };

  // 전체 선택/해제
  const toggleAllSelection = () => {
    if (selectedLines.size === lines.length) {
      setSelectedLines(new Set());
    } else {
      setSelectedLines(new Set(lines.map((l) => l.id)));
    }
  };

  // 전표 입력
  const handlePostSaleForms = (mergeToSingleDocument: boolean) => {
    if (selectedLines.size === 0) {
      toast.error('라인을 선택해주세요');
      return;
    }

    if (
      confirm(
        `선택한 ${selectedLines.size}개 라인을 ${
          mergeToSingleDocument ? '한 전표로' : '개별 전표로'
        } 입력하시겠습니까?`
      )
    ) {
      postSaleFormsMutation.mutate(mergeToSingleDocument);
    }
  };

  // 템플릿 편집 시작
  const startEditTemplate = (template: SaleFormTemplate) => {
    setEditingTemplate(template);
    setTemplateForm({
      templateName: template.templateName,
      isDefault: template.isDefault,
      description: template.description || '',
      defaultCustomerCode: template.defaultCustomerCode || '',
      defaultWarehouseCode: template.defaultWarehouseCode || '00009',
      defaultIoType: template.defaultIoType || '',
      defaultEmpCd: template.defaultEmpCd || '',
      defaultSite: template.defaultSite || '',
    });
    setShowTemplateDialog(true);
  };

  // 라인 편집 시작
  const startEditLine = (line: SaleFormLine) => {
    setEditingLine(line);
    setLineForm({
      ioDate: line.ioDate,
      cust: line.cust,
      custDes: line.custDes || '',
      empCd: line.empCd || '',
      whCd: line.whCd || '00009',
      ioType: line.ioType || '',
      prodCd: line.prodCd,
      prodDes: line.prodDes || '',
      sizeDes: line.sizeDes || '',
      qty: line.qty,
      price: line.price,
      supplyAmt: line.supplyAmt || 0,
      vatAmt: line.vatAmt || 0,
      remarks: line.remarks || '',
      site: line.site || '',
      pjtCd: line.pjtCd || '',
    });
    setShowLineDialog(true);
  };

  // 상태 뱃지
  const getStatusBadge = (status: string) => {
    const variants: Record<string, 'default' | 'secondary' | 'destructive' | 'outline'> = {
      DRAFT: 'secondary',
      PENDING: 'default',
      POSTED: 'outline',
      FAILED: 'destructive',
    };

    const labels: Record<string, string> = {
      DRAFT: '임시저장',
      PENDING: '대기중',
      POSTED: '입력완료',
      FAILED: '실패',
    };

    return (
      <Badge variant={variants[status] || 'default'}>
        {labels[status] || status}
      </Badge>
    );
  };

  if (loading) {
    return <Loading />;
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="전표입력"
        description="이카운트 판매전표 입력"
        actions={
          <div className="flex gap-2">
            <Button
              variant="outline"
              onClick={() => {
                setEditingTemplate(null);
                setTemplateForm({
                  templateName: '',
                  isDefault: false,
                  description: '',
                  defaultCustomerCode: '',
                  defaultWarehouseCode: '00009',
                  defaultIoType: '',
                  defaultEmpCd: '',
                  defaultSite: '',
                });
                setShowTemplateDialog(true);
              }}
            >
              <FileText className="w-4 h-4 mr-2" />
              템플릿 관리
            </Button>
            <Button
              onClick={() => {
                setEditingLine(null);
                setLineForm({
                  ioDate: new Date().toISOString().split('T')[0].replace(/-/g, ''),
                  cust: '',
                  custDes: '',
                  empCd: '',
                  whCd: '00009',
                  ioType: '',
                  prodCd: '',
                  prodDes: '',
                  sizeDes: '',
                  qty: 1,
                  price: 0,
                  supplyAmt: 0,
                  vatAmt: 0,
                  remarks: '',
                  site: '',
                  pjtCd: '',
                });
                setShowLineDialog(true);
              }}
            >
              <Plus className="w-4 h-4 mr-2" />
              라인 추가
            </Button>
          </div>
        }
      />

      {/* 템플릿 목록 */}
      {templates.length > 0 && (
        <Card className="p-4">
          <h3 className="text-lg font-semibold mb-4">템플릿</h3>
          <div className="space-y-2">
            {templates.map((template) => (
              <div
                key={template.id}
                className="flex items-center justify-between p-3 border rounded-lg"
              >
                <div className="flex-1">
                  <div className="font-medium">{template.templateName}</div>
                  {template.description && (
                    <div className="text-sm text-muted-foreground">
                      {template.description}
                    </div>
                  )}
                  {template.isDefault && (
                    <Badge variant="outline" className="mt-1">기본</Badge>
                  )}
                </div>
                <div className="flex gap-2">
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => startEditTemplate(template)}
                  >
                    <Edit className="w-4 h-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => handleDeleteTemplate(template.id)}
                  >
                    <Trash2 className="w-4 h-4" />
                  </Button>
                </div>
              </div>
            ))}
          </div>
        </Card>
      )}

      {/* 전표 라인 목록 */}
      <Card className="p-6">
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-lg font-semibold">전표 라인</h3>
          {selectedLines.size > 0 && (
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => handlePostSaleForms(false)}
                disabled={postSaleFormsMutation.isPending}
              >
                <Send className="w-4 h-4 mr-2" />
                개별 전표로 입력 ({selectedLines.size})
              </Button>
              <Button
                size="sm"
                onClick={() => handlePostSaleForms(true)}
                disabled={postSaleFormsMutation.isPending}
              >
                <Send className="w-4 h-4 mr-2" />
                한 전표로 입력 ({selectedLines.size})
              </Button>
            </div>
          )}
        </div>

        {lines.length === 0 ? (
          <EmptyState
            icon={<FileText className="h-12 w-12" />}
            title="라인이 없습니다"
            description="라인 추가 버튼을 눌러 새 라인을 추가하세요"
          />
        ) : (
          <div className="border rounded-lg overflow-hidden">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-12">
                    <button
                      onClick={toggleAllSelection}
                      className="flex items-center justify-center w-full"
                    >
                      {selectedLines.size === lines.length ? (
                        <CheckSquare className="w-5 h-5" />
                      ) : (
                        <Square className="w-5 h-5" />
                      )}
                    </button>
                  </TableHead>
                  <TableHead>일자</TableHead>
                  <TableHead>거래처</TableHead>
                  <TableHead>품목코드</TableHead>
                  <TableHead>품목명</TableHead>
                  <TableHead className="text-right">수량</TableHead>
                  <TableHead className="text-right">단가</TableHead>
                  <TableHead className="text-right">금액</TableHead>
                  <TableHead>상태</TableHead>
                  <TableHead>전표번호</TableHead>
                  <TableHead className="w-24">작업</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {lines.map((line) => (
                  <TableRow key={line.id}>
                    <TableCell>
                      <button
                        onClick={() => toggleLineSelection(line.id)}
                        disabled={line.status === 'POSTED'}
                        className="flex items-center justify-center w-full disabled:opacity-50"
                      >
                        {selectedLines.has(line.id) ? (
                          <CheckSquare className="w-5 h-5" />
                        ) : (
                          <Square className="w-5 h-5" />
                        )}
                      </button>
                    </TableCell>
                    <TableCell>
                      {line.ioDate.replace(/(\d{4})(\d{2})(\d{2})/, '$1-$2-$3')}
                    </TableCell>
                    <TableCell>
                      <div className="font-mono text-sm">{line.cust}</div>
                      {line.custDes && (
                        <div className="text-xs text-muted-foreground">{line.custDes}</div>
                      )}
                    </TableCell>
                    <TableCell>
                      <div className="font-mono text-sm">{line.prodCd}</div>
                    </TableCell>
                    <TableCell>
                      {line.prodDes}
                      {line.sizeDes && (
                        <div className="text-xs text-muted-foreground">{line.sizeDes}</div>
                      )}
                    </TableCell>
                    <TableCell className="text-right">{line.qty}</TableCell>
                    <TableCell className="text-right">
                      {line.price.toLocaleString()}원
                    </TableCell>
                    <TableCell className="text-right">
                      {line.supplyAmt?.toLocaleString() || 0}원
                    </TableCell>
                    <TableCell>{getStatusBadge(line.status)}</TableCell>
                    <TableCell>
                      {line.docNo && (
                        <div className="font-mono text-sm">{line.docNo}</div>
                      )}
                    </TableCell>
                    <TableCell>
                      <div className="flex gap-1">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => startEditLine(line)}
                          disabled={line.status === 'POSTED'}
                        >
                          <Edit className="w-4 h-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleDeleteLine(line.id)}
                          disabled={line.status === 'POSTED'}
                        >
                          <Trash2 className="w-4 h-4" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </Card>

      {/* 템플릿 다이얼로그 */}
      <Dialog open={showTemplateDialog} onOpenChange={setShowTemplateDialog}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {editingTemplate ? '템플릿 수정' : '템플릿 생성'}
            </DialogTitle>
            <DialogDescription>
              전표입력 시 사용할 기본값을 설정합니다
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div>
              <Label htmlFor="templateName">템플릿 이름 *</Label>
              <Input
                id="templateName"
                value={templateForm.templateName}
                onChange={(e) =>
                  setTemplateForm({ ...templateForm, templateName: e.target.value })
                }
                placeholder="예: 기본 판매전표"
              />
            </div>

            <div>
              <Label htmlFor="description">설명</Label>
              <Input
                id="description"
                value={templateForm.description}
                onChange={(e) =>
                  setTemplateForm({ ...templateForm, description: e.target.value })
                }
                placeholder="템플릿 설명"
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <Label htmlFor="defaultCustomerCode">기본 거래처코드</Label>
                <Input
                  id="defaultCustomerCode"
                  value={templateForm.defaultCustomerCode}
                  onChange={(e) =>
                    setTemplateForm({
                      ...templateForm,
                      defaultCustomerCode: e.target.value,
                    })
                  }
                />
              </div>

              <div>
                <Label htmlFor="defaultWarehouseCode">기본 창고코드</Label>
                <Input
                  id="defaultWarehouseCode"
                  value={templateForm.defaultWarehouseCode}
                  onChange={(e) =>
                    setTemplateForm({
                      ...templateForm,
                      defaultWarehouseCode: e.target.value,
                    })
                  }
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <Label htmlFor="defaultEmpCd">기본 담당자</Label>
                <Input
                  id="defaultEmpCd"
                  value={templateForm.defaultEmpCd}
                  onChange={(e) =>
                    setTemplateForm({ ...templateForm, defaultEmpCd: e.target.value })
                  }
                />
              </div>

              <div>
                <Label htmlFor="defaultSite">기본 부서</Label>
                <Input
                  id="defaultSite"
                  value={templateForm.defaultSite}
                  onChange={(e) =>
                    setTemplateForm({ ...templateForm, defaultSite: e.target.value })
                  }
                />
              </div>
            </div>

            <div className="flex items-center gap-2">
              <input
                type="checkbox"
                id="isDefault"
                checked={templateForm.isDefault}
                onChange={(e) =>
                  setTemplateForm({ ...templateForm, isDefault: e.target.checked })
                }
                className="w-4 h-4"
              />
              <Label htmlFor="isDefault">기본 템플릿으로 설정</Label>
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setShowTemplateDialog(false)}>
              취소
            </Button>
            <Button onClick={handleSaveTemplate}>저장</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 라인 다이얼로그 */}
      <Dialog open={showLineDialog} onOpenChange={setShowLineDialog}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{editingLine ? '라인 수정' : '라인 추가'}</DialogTitle>
            <DialogDescription>
              전표에 입력할 판매 정보를 입력합니다
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 max-h-[60vh] overflow-y-auto pr-2">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <Label htmlFor="ioDate">판매일자 (YYYYMMDD) *</Label>
                <Input
                  id="ioDate"
                  value={lineForm.ioDate}
                  onChange={(e) => setLineForm({ ...lineForm, ioDate: e.target.value })}
                  placeholder="20260115"
                />
              </div>

              <div>
                <Label htmlFor="cust">거래처코드 *</Label>
                <Input
                  id="cust"
                  value={lineForm.cust}
                  onChange={(e) => setLineForm({ ...lineForm, cust: e.target.value })}
                />
              </div>
            </div>

            <div>
              <Label htmlFor="custDes">거래처명</Label>
              <Input
                id="custDes"
                value={lineForm.custDes}
                onChange={(e) => setLineForm({ ...lineForm, custDes: e.target.value })}
              />
            </div>

            <div className="grid grid-cols-3 gap-4">
              <div>
                <Label htmlFor="whCd">창고코드</Label>
                <Input
                  id="whCd"
                  value={lineForm.whCd}
                  onChange={(e) => setLineForm({ ...lineForm, whCd: e.target.value })}
                />
              </div>

              <div>
                <Label htmlFor="empCd">담당자</Label>
                <Input
                  id="empCd"
                  value={lineForm.empCd}
                  onChange={(e) => setLineForm({ ...lineForm, empCd: e.target.value })}
                />
              </div>

              <div>
                <Label htmlFor="site">부서</Label>
                <Input
                  id="site"
                  value={lineForm.site}
                  onChange={(e) => setLineForm({ ...lineForm, site: e.target.value })}
                />
              </div>
            </div>

            <div className="border-t pt-4">
              <h4 className="font-medium mb-4">품목 정보</h4>

              <div className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <Label htmlFor="prodCd">품목코드 *</Label>
                    <Input
                      id="prodCd"
                      value={lineForm.prodCd}
                      onChange={(e) =>
                        setLineForm({ ...lineForm, prodCd: e.target.value })
                      }
                    />
                  </div>

                  <div>
                    <Label htmlFor="prodDes">품목명</Label>
                    <Input
                      id="prodDes"
                      value={lineForm.prodDes}
                      onChange={(e) =>
                        setLineForm({ ...lineForm, prodDes: e.target.value })
                      }
                    />
                  </div>
                </div>

                <div>
                  <Label htmlFor="sizeDes">규격</Label>
                  <Input
                    id="sizeDes"
                    value={lineForm.sizeDes}
                    onChange={(e) =>
                      setLineForm({ ...lineForm, sizeDes: e.target.value })
                    }
                  />
                </div>

                <div className="grid grid-cols-3 gap-4">
                  <div>
                    <Label htmlFor="qty">수량 *</Label>
                    <Input
                      id="qty"
                      type="number"
                      value={lineForm.qty}
                      onChange={(e) =>
                        setLineForm({ ...lineForm, qty: Number(e.target.value) })
                      }
                    />
                  </div>

                  <div>
                    <Label htmlFor="price">단가 *</Label>
                    <Input
                      id="price"
                      type="number"
                      value={lineForm.price}
                      onChange={(e) =>
                        setLineForm({ ...lineForm, price: Number(e.target.value) })
                      }
                    />
                  </div>

                  <div>
                    <Label htmlFor="supplyAmt">공급가액</Label>
                    <Input
                      id="supplyAmt"
                      type="number"
                      value={lineForm.supplyAmt}
                      onChange={(e) =>
                        setLineForm({ ...lineForm, supplyAmt: Number(e.target.value) })
                      }
                    />
                  </div>
                </div>

                <div>
                  <Label htmlFor="remarks">적요</Label>
                  <Input
                    id="remarks"
                    value={lineForm.remarks}
                    onChange={(e) =>
                      setLineForm({ ...lineForm, remarks: e.target.value })
                    }
                  />
                </div>
              </div>
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setShowLineDialog(false)}>
              취소
            </Button>
            <Button onClick={handleSaveLine}>저장</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
