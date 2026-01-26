'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Plus, Store as StoreIcon, Settings, Trash2, Power, PowerOff, Database, Zap, ZapOff, Building2, DollarSign } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Switch } from '@/components/ui/switch';
import { PageHeader } from '@/components/layout';
import { Loading, EmptyState } from '@/components/common';
import { storeApi, credentialApi, erpApi } from '@/lib/api';
import { useAuthStore } from '@/lib/stores/auth-store';
import { formatDate } from '@/lib/utils';
import { toast } from 'sonner';
import type { Store, CreateStoreRequest, SaveCredentialRequest, Marketplace, ErpConfig, UpdateErpConfigRequest } from '@/types';

export default function IntegrationsPage() {
  const queryClient = useQueryClient();
  const { user } = useAuthStore();
  const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false);
  const [isCredentialDialogOpen, setIsCredentialDialogOpen] = useState(false);
  const [isErpConfigDialogOpen, setIsErpConfigDialogOpen] = useState(false);
  const [isErpCredentialDialogOpen, setIsErpCredentialDialogOpen] = useState(false);
  const [selectedStore, setSelectedStore] = useState<Store | null>(null);
  const [isCustomerCodeDialogOpen, setIsCustomerCodeDialogOpen] = useState(false);
  const [customerCodeStore, setCustomerCodeStore] = useState<Store | null>(null);
  const [isCommissionItemsDialogOpen, setIsCommissionItemsDialogOpen] = useState(false);
  const [commissionItemsStore, setCommissionItemsStore] = useState<Store | null>(null);

  // 스토어 목록 조회
  const { data: stores, isLoading } = useQuery({
    queryKey: ['stores'],
    queryFn: () => storeApi.getStores(),
  });

  // ERP 설정 조회
  const { data: erpConfig, isLoading: isErpLoading } = useQuery({
    queryKey: ['erp-config', 'ECOUNT'],
    queryFn: () => erpApi.getErpConfig('ECOUNT'),
  });

  // 스토어 생성 mutation
  const createStoreMutation = useMutation({
    mutationFn: storeApi.createStore,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stores'] });
      setIsCreateDialogOpen(false);
      toast.success('스토어가 생성되었습니다');
    },
    onError: (error: any) => {
      toast.error(error.message || '스토어 생성에 실패했습니다');
    },
  });

  // 스토어 상태 변경 mutation
  const updateStatusMutation = useMutation({
    mutationFn: ({ storeId, isActive }: { storeId: string; isActive: boolean }) =>
      storeApi.updateStoreStatus(storeId, isActive),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stores'] });
      toast.success('스토어 상태가 변경되었습니다');
    },
    onError: (error: any) => {
      toast.error(error.message || '상태 변경에 실패했습니다');
    },
  });

  // 스토어 삭제 mutation
  const deleteStoreMutation = useMutation({
    mutationFn: storeApi.deleteStore,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stores'] });
      toast.success('스토어가 삭제되었습니다');
    },
    onError: (error: any) => {
      toast.error(error.message || '스토어 삭제에 실패했습니다');
    },
  });

  // 스토어 거래처코드 업데이트 mutation
  const updateCustomerCodeMutation = useMutation({
    mutationFn: ({ storeId, erpCustomerCode }: { storeId: string; erpCustomerCode: string }) =>
      storeApi.updateErpCustomerCode(storeId, erpCustomerCode),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stores'] });
      setIsCustomerCodeDialogOpen(false);
      setCustomerCodeStore(null);
      toast.success('거래처 코드가 설정되었습니다');
    },
    onError: (error: any) => {
      toast.error(error.message || '거래처 코드 설정에 실패했습니다');
    },
  });

  // 스토어 수수료 품목 코드 업데이트 mutation
  const updateCommissionItemsMutation = useMutation({
    mutationFn: ({ 
      storeId, 
      commissionItemCode, 
      shippingCommissionItemCode,
      shippingItemCode
    }: { 
      storeId: string; 
      commissionItemCode?: string; 
      shippingCommissionItemCode?: string;
      shippingItemCode?: string;
    }) => storeApi.updateCommissionItems(storeId, commissionItemCode, shippingCommissionItemCode, shippingItemCode),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stores'] });
      setIsCommissionItemsDialogOpen(false);
      setCommissionItemsStore(null);
      toast.success('수수료 품목 코드가 설정되었습니다');
    },
    onError: (error: any) => {
      toast.error(error.message || '수수료 품목 코드 설정에 실패했습니다');
    },
  });

  // Credential 저장 mutation
  const saveCredentialMutation = useMutation({
    mutationFn: credentialApi.saveCredential,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['credentials'] });
      setIsCredentialDialogOpen(false);
      toast.success('연동 정보가 저장되었습니다');
    },
    onError: (error: any) => {
      toast.error(error.message || '연동 정보 저장에 실패했습니다');
    },
  });

  // ERP 자동 전표 생성 토글 mutation
  const toggleAutoPostingMutation = useMutation({
    mutationFn: ({ enable }: { enable: boolean }) =>
      erpApi.toggleAutoPosting('ECOUNT', { enable }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['erp-config'] });
      toast.success('자동 전표 생성 설정이 변경되었습니다');
    },
    onError: (error: any) => {
      toast.error(error.message || '설정 변경에 실패했습니다');
    },
  });

  // ERP 자동 전송 토글 mutation
  const toggleAutoSendMutation = useMutation({
    mutationFn: ({ enable }: { enable: boolean }) =>
      erpApi.toggleAutoSend('ECOUNT', { enable }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['erp-config'] });
      toast.success('자동 전송 설정이 변경되었습니다');
    },
    onError: (error: any) => {
      toast.error(error.message || '설정 변경에 실패했습니다');
    },
  });

  // ERP 기본 설정 업데이트 mutation
  const updateErpConfigMutation = useMutation({
    mutationFn: (data: UpdateErpConfigRequest) =>
      erpApi.updateErpConfig('ECOUNT', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['erp-config'] });
      setIsErpConfigDialogOpen(false);
      toast.success('ERP 설정이 업데이트되었습니다');
    },
    onError: (error: any) => {
      toast.error(error.message || 'ERP 설정 업데이트에 실패했습니다');
    },
  });

  // ERP Credential 저장 중 상태 관리
  const [isErpCredentialSaving, setIsErpCredentialSaving] = useState(false);

  const handleCreateStore = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const formData = new FormData(e.currentTarget);
    
    const data: CreateStoreRequest = {
      tenantId: user?.tenantId || '',
      storeName: formData.get('storeName') as string,
      marketplace: formData.get('marketplace') as Marketplace,
      externalStoreId: formData.get('externalStoreId') as string || undefined,
      // 수수료 품목 코드
      commissionItemCode: formData.get('commissionItemCode') as string,
      shippingCommissionItemCode: formData.get('shippingCommissionItemCode') as string,
      // 기본 설정
      defaultWarehouseCode: formData.get('defaultWarehouseCode') as string || '100',
      defaultCustomerCode: formData.get('defaultCustomerCode') as string,
      shippingItemCode: formData.get('shippingItemCode') as string,
    };

    createStoreMutation.mutate(data);
  };

  // 마켓플레이스 Credential 저장 중 상태 관리
  const [isMarketplaceCredentialSaving, setIsMarketplaceCredentialSaving] = useState(false);

  const handleSaveCredential = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!selectedStore) return;

    setIsMarketplaceCredentialSaving(true);
    const formData = new FormData(e.currentTarget);
    
    const credentials: SaveCredentialRequest[] = [];

    // 마켓플레이스별 필요한 credential 수집
    if (selectedStore.marketplace === 'NAVER_SMARTSTORE') {
      credentials.push(
        {
          tenantId: user?.tenantId || '',
          storeId: selectedStore.storeId,
          credentialType: 'MARKETPLACE',
          keyName: 'CLIENT_ID',
          secretValue: formData.get('clientId') as string,
          description: '네이버 스마트스토어 Client ID',
        },
        {
          tenantId: user?.tenantId || '',
          storeId: selectedStore.storeId,
          credentialType: 'MARKETPLACE',
          keyName: 'CLIENT_SECRET',
          secretValue: formData.get('clientSecret') as string,
          description: '네이버 스마트스토어 Client Secret',
        }
      );
    } else if (selectedStore.marketplace === 'COUPANG') {
      credentials.push(
        {
          tenantId: user?.tenantId || '',
          storeId: selectedStore.storeId,
          credentialType: 'MARKETPLACE',
          keyName: 'VENDOR_ID',
          secretValue: formData.get('vendorId') as string,
          description: '쿠팡 Vendor ID',
        },
        {
          tenantId: user?.tenantId || '',
          storeId: selectedStore.storeId,
          credentialType: 'MARKETPLACE',
          keyName: 'ACCESS_KEY',
          secretValue: formData.get('accessKey') as string,
          description: '쿠팡 Access Key',
        },
        {
          tenantId: user?.tenantId || '',
          storeId: selectedStore.storeId,
          credentialType: 'MARKETPLACE',
          keyName: 'SECRET_KEY',
          secretValue: formData.get('secretKey') as string,
          description: '쿠팡 Secret Key',
        }
      );
    }

    try {
      // 모든 credential을 순차적으로 저장
      for (const credential of credentials) {
        await credentialApi.saveCredential(credential);
      }
      
      queryClient.invalidateQueries({ queryKey: ['credentials'] });
      setIsCredentialDialogOpen(false);
      setSelectedStore(null);
      toast.success('연동 정보가 모두 저장되었습니다');
    } catch (error: any) {
      toast.error(error.message || '연동 정보 저장에 실패했습니다');
    } finally {
      setIsMarketplaceCredentialSaving(false);
    }
  };

  const handleToggleStatus = (store: Store) => {
    updateStatusMutation.mutate({
      storeId: store.storeId,
      isActive: !store.isActive,
    });
  };

  const handleDeleteStore = (storeId: string) => {
    if (confirm('정말 이 스토어를 삭제하시겠습니까?')) {
      deleteStoreMutation.mutate(storeId);
    }
  };

  const openCredentialDialog = (store: Store) => {
    setSelectedStore(store);
    setIsCredentialDialogOpen(true);
  };

  const openCustomerCodeDialog = (store: Store) => {
    setCustomerCodeStore(store);
    setIsCustomerCodeDialogOpen(true);
  };

  const openCommissionItemsDialog = (store: Store) => {
    setCommissionItemsStore(store);
    setIsCommissionItemsDialogOpen(true);
  };

  const handleUpdateCustomerCode = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!customerCodeStore) return;

    const formData = new FormData(e.currentTarget);
    const erpCustomerCode = formData.get('erpCustomerCode') as string;

    updateCustomerCodeMutation.mutate({
      storeId: customerCodeStore.storeId,
      erpCustomerCode,
    });
  };

  const handleUpdateCommissionItems = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!commissionItemsStore) return;

    const formData = new FormData(e.currentTarget);
    const shippingItemCode = formData.get('shippingItemCode') as string;
    const commissionItemCode = formData.get('commissionItemCode') as string;
    const shippingCommissionItemCode = formData.get('shippingCommissionItemCode') as string;

    updateCommissionItemsMutation.mutate({
      storeId: commissionItemsStore.storeId,
      shippingItemCode: shippingItemCode || undefined,
      commissionItemCode: commissionItemCode || undefined,
      shippingCommissionItemCode: shippingCommissionItemCode || undefined,
    });
  };

  const handleUpdateErpConfig = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const formData = new FormData(e.currentTarget);
    
    const data: UpdateErpConfigRequest = {
      defaultCustomerCode: formData.get('defaultCustomerCode') as string || undefined,
      defaultWarehouseCode: formData.get('defaultWarehouseCode') as string || undefined,
      shippingItemCode: formData.get('shippingItemCode') as string || undefined,
      postingBatchSize: formData.get('postingBatchSize') 
        ? parseInt(formData.get('postingBatchSize') as string) 
        : undefined,
      maxRetryCount: formData.get('maxRetryCount') 
        ? parseInt(formData.get('maxRetryCount') as string) 
        : undefined,
    };

    updateErpConfigMutation.mutate(data);
  };

  const handleToggleAutoPosting = (checked: boolean) => {
    toggleAutoPostingMutation.mutate({ enable: checked });
  };

  const handleToggleAutoSend = (checked: boolean) => {
    toggleAutoSendMutation.mutate({ enable: checked });
  };

  const handleSaveErpCredential = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setIsErpCredentialSaving(true);
    
    const formData = new FormData(e.currentTarget);
    
    // ECOUNT_CONFIG로 단일 JSON credential 저장
    // 백엔드에서 zone 정보를 자동으로 조회하여 추가함
    const ecountConfig = {
      comCode: formData.get('comCode') as string,
      userId: formData.get('userId') as string,
      apiKey: formData.get('apiCertKey') as string,
      // zone은 백엔드에서 자동 조회하므로 빈 문자열 전달
      zone: (formData.get('zone') as string) || '',
    };

    const credential = {
      tenantId: user?.tenantId || '',
      storeId: null,
      credentialType: 'ERP',
      keyName: 'ECOUNT_CONFIG',
      secretValue: JSON.stringify(ecountConfig),
      description: '이카운트 ERP 연동 설정',
    };

    try {
      // ECOUNT_CONFIG 단일 credential 저장
      await credentialApi.saveCredential(credential);
      
      queryClient.invalidateQueries({ queryKey: ['erp-credentials'] });
      setIsErpCredentialDialogOpen(false);
      toast.success('ERP 연동 정보가 저장되었습니다. Zone 정보가 자동으로 설정됩니다.');
    } catch (error: any) {
      toast.error(error.message || 'ERP 연동 정보 저장에 실패했습니다');
    } finally {
      setIsErpCredentialSaving(false);
    }
  };

  const getMarketplaceLabel = (marketplace: string) => {
    const labels: Record<string, string> = {
      NAVER_SMARTSTORE: '네이버 스마트스토어',
      COUPANG: '쿠팡',
    };
    return labels[marketplace] || marketplace;
  };

  if (isLoading || isErpLoading) {
    return <Loading />;
  }

  return (
    <div className="space-y-8">
      <PageHeader
        title="연동 설정"
        description="마켓플레이스 및 ERP 연동을 관리합니다"
      />

      {/* ERP 설정 섹션 */}
      <section className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-lg font-semibold flex items-center gap-2">
              <Database className="h-5 w-5" />
              ERP 연동 설정
            </h2>
            <p className="text-sm text-muted-foreground mt-1">
              전표 자동화 및 기본 설정을 관리합니다
            </p>
          </div>
          <div className="flex gap-2">
            <Dialog open={isErpCredentialDialogOpen} onOpenChange={setIsErpCredentialDialogOpen}>
              <DialogTrigger asChild>
                <Button variant="outline">
                  <Settings className="mr-2 h-4 w-4" />
                  API 연동
                </Button>
              </DialogTrigger>
              <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
                <DialogHeader>
                  <DialogTitle>ERP API 연동 정보</DialogTitle>
                  <DialogDescription>
                    ECOUNT ERP 연동에 필요한 정보를 입력하세요
                  </DialogDescription>
                </DialogHeader>
                <form onSubmit={handleSaveErpCredential} className="space-y-6">
                  {/* 필수 정보 섹션 */}
                  <div className="space-y-4">
                    <div className="pb-2 border-b">
                      <h3 className="font-semibold text-sm">필수 정보</h3>
                      <p className="text-xs text-muted-foreground mt-1">
                        ECOUNT API 사용을 위해 반드시 필요한 정보입니다
                      </p>
                    </div>
                    
                    <div className="space-y-2">
                      <Label htmlFor="comCode">
                        회사코드 (COM_CODE)
                        <span className="text-destructive ml-1">*</span>
                      </Label>
                      <Input
                        id="comCode"
                        name="comCode"
                        placeholder="예: 89021"
                        required
                      />
                      <p className="text-xs text-muted-foreground">
                        ECOUNT에서 발급받은 회사코드를 입력하세요
                      </p>
                    </div>

                    <div className="space-y-2">
                      <Label htmlFor="userId">
                        사용자 ID (USER_ID)
                        <span className="text-destructive ml-1">*</span>
                      </Label>
                      <Input
                        id="userId"
                        name="userId"
                        placeholder="예: USER_ID"
                        required
                      />
                      <p className="text-xs text-muted-foreground">
                        ECOUNT 로그인 사용자 ID를 입력하세요
                      </p>
                    </div>

                    <div className="space-y-2">
                      <Label htmlFor="apiCertKey">
                        API 인증키 (API_CERT_KEY)
                        <span className="text-destructive ml-1">*</span>
                      </Label>
                      <Input
                        id="apiCertKey"
                        name="apiCertKey"
                        type="password"
                        placeholder="API 인증키를 입력하세요"
                        required
                      />
                      <p className="text-xs text-muted-foreground">
                        Self-Customizing &gt; 정보관리 &gt; API인증키관리에서 발급받은 키
                      </p>
                    </div>
                  </div>

                  {/* 자동 설정 정보 */}
                  <div className="space-y-4">
                    <div className="pb-2 border-b">
                      <h3 className="font-semibold text-sm">자동 설정 정보</h3>
                      <p className="text-xs text-muted-foreground mt-1">
                        다음 정보는 자동으로 설정됩니다
                      </p>
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                      <div className="space-y-2">
                        <Label htmlFor="zone">Zone</Label>
                        <Input
                          id="zone"
                          name="zone"
                          value="자동 조회"
                          disabled
                          className="bg-muted"
                        />
                        <p className="text-xs text-muted-foreground">
                          회사코드 기반 자동 조회
                        </p>
                      </div>

                      <div className="space-y-2">
                        <Label htmlFor="lanType">언어타입</Label>
                        <Input
                          id="lanType"
                          name="lanType"
                          value="ko-KR (한국어)"
                          disabled
                          className="bg-muted"
                        />
                        <p className="text-xs text-muted-foreground">
                          한국어로 고정
                        </p>
                      </div>
                    </div>
                  </div>

                  {/* 안내 메시지 */}
                  <div className="rounded-lg bg-muted p-4">
                    <div className="flex gap-2">
                      <div className="text-sm text-muted-foreground">
                        <p className="font-medium text-foreground mb-2">📌 안내사항</p>
                        <ul className="space-y-1 list-disc list-inside">
                          <li>모든 정보는 암호화되어 안전하게 저장됩니다</li>
                          <li>API 인증키는 ECOUNT Self-Customizing에서 발급받을 수 있습니다</li>
                          <li>정보 입력 후 &quot;ERP 연동 테스트&quot; 버튼으로 연결을 확인하세요</li>
                        </ul>
                      </div>
                    </div>
                  </div>

                  <div className="flex justify-end gap-2">
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => setIsErpCredentialDialogOpen(false)}
                      disabled={isErpCredentialSaving}
                    >
                      취소
                    </Button>
                    <Button type="submit" disabled={isErpCredentialSaving}>
                      {isErpCredentialSaving ? '저장 중...' : '저장'}
                    </Button>
                  </div>
                </form>
              </DialogContent>
            </Dialog>
            <Dialog open={isErpConfigDialogOpen} onOpenChange={setIsErpConfigDialogOpen}>
              <DialogTrigger asChild>
                <Button variant="outline">
                  <Settings className="mr-2 h-4 w-4" />
                  기본 설정
                </Button>
              </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>ERP 기본 설정</DialogTitle>
                <DialogDescription>
                  거래처, 창고, 배송비 품목 등의 기본값을 설정합니다
                </DialogDescription>
              </DialogHeader>
              <form onSubmit={handleUpdateErpConfig} className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="defaultCustomerCode">기본 거래처 코드</Label>
                  <Input
                    id="defaultCustomerCode"
                    name="defaultCustomerCode"
                    defaultValue={erpConfig?.defaultCustomerCode || ''}
                    placeholder="예: ONLINE"
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="defaultWarehouseCode">기본 창고 코드</Label>
                  <Input
                    id="defaultWarehouseCode"
                    name="defaultWarehouseCode"
                    defaultValue={erpConfig?.defaultWarehouseCode || ''}
                    placeholder="예: 001"
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="shippingItemCode">배송비 품목 코드</Label>
                  <Input
                    id="shippingItemCode"
                    name="shippingItemCode"
                    defaultValue={erpConfig?.shippingItemCode || ''}
                    placeholder="예: SHIPPING"
                  />
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="postingBatchSize">배치 처리 개수</Label>
                    <Input
                      id="postingBatchSize"
                      name="postingBatchSize"
                      type="number"
                      defaultValue={erpConfig?.postingBatchSize || 10}
                      min="1"
                      max="100"
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="maxRetryCount">최대 재시도 횟수</Label>
                    <Input
                      id="maxRetryCount"
                      name="maxRetryCount"
                      type="number"
                      defaultValue={erpConfig?.maxRetryCount || 3}
                      min="0"
                      max="10"
                    />
                  </div>
                </div>
                <div className="flex justify-end gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setIsErpConfigDialogOpen(false)}
                  >
                    취소
                  </Button>
                  <Button type="submit" disabled={updateErpConfigMutation.isPending}>
                    {updateErpConfigMutation.isPending ? '저장 중...' : '저장'}
                  </Button>
                </div>
              </form>
            </DialogContent>
          </Dialog>
          </div>
        </div>

        {erpConfig && (
          <Card className="p-6">
            <div className="space-y-6">
              {/* ERP 정보 */}
              <div className="flex items-center justify-between pb-4 border-b">
                <div>
                  <h3 className="font-semibold text-lg">{erpConfig.erpCode}</h3>
                  <p className="text-sm text-muted-foreground">
                    마지막 업데이트: {formatDate(erpConfig.updatedAt)}
                  </p>
                </div>
                <Badge variant={erpConfig.enabled ? 'default' : 'secondary'}>
                  {erpConfig.enabled ? '활성' : '비활성'}
                </Badge>
              </div>

              {/* 자동화 설정 */}
              <div className="space-y-4">
                <h4 className="font-medium text-sm text-muted-foreground">자동화 설정</h4>
                
                {/* 자동 전표 생성 */}
                <div className="flex items-center justify-between p-4 rounded-lg border bg-card">
                  <div className="flex items-start gap-3">
                    <Zap className={`h-5 w-5 mt-0.5 ${erpConfig.autoPostingEnabled ? 'text-primary' : 'text-muted-foreground'}`} />
                    <div>
                      <div className="font-medium">자동 전표 생성</div>
                      <p className="text-sm text-muted-foreground mt-1">
                        정산 데이터가 준비되면 10분 이내 자동으로 전표를 생성합니다
                      </p>
                    </div>
                  </div>
                  <Switch
                    checked={erpConfig.autoPostingEnabled}
                    onCheckedChange={handleToggleAutoPosting}
                    disabled={toggleAutoPostingMutation.isPending}
                  />
                </div>

                {/* 자동 전송 */}
                <div className="flex items-center justify-between p-4 rounded-lg border bg-card">
                  <div className="flex items-start gap-3">
                    <Zap className={`h-5 w-5 mt-0.5 ${erpConfig.autoSendEnabled ? 'text-primary' : 'text-muted-foreground'}`} />
                    <div>
                      <div className="font-medium">자동 ERP 전송</div>
                      <p className="text-sm text-muted-foreground mt-1">
                        전표가 준비되면 1분 이내 자동으로 ERP에 전송합니다
                      </p>
                    </div>
                  </div>
                  <Switch
                    checked={erpConfig.autoSendEnabled}
                    onCheckedChange={handleToggleAutoSend}
                    disabled={toggleAutoSendMutation.isPending}
                  />
                </div>
              </div>

              {/* 기본 설정 정보 */}
              <div className="space-y-3">
                <h4 className="font-medium text-sm text-muted-foreground">기본 설정</h4>
                <div className="grid grid-cols-2 gap-4 text-sm">
                  <div className="space-y-1">
                    <span className="text-muted-foreground">거래처 코드</span>
                    <p className="font-medium">{erpConfig.defaultCustomerCode || '-'}</p>
                  </div>
                  <div className="space-y-1">
                    <span className="text-muted-foreground">창고 코드</span>
                    <p className="font-medium">{erpConfig.defaultWarehouseCode || '-'}</p>
                  </div>
                  <div className="space-y-1">
                    <span className="text-muted-foreground">배송비 품목</span>
                    <p className="font-medium">{erpConfig.shippingItemCode || '-'}</p>
                  </div>
                  <div className="space-y-1">
                    <span className="text-muted-foreground">배치 크기</span>
                    <p className="font-medium">{erpConfig.postingBatchSize || 10}개</p>
                  </div>
                </div>
              </div>
            </div>
          </Card>
        )}
      </section>

      {/* 마켓플레이스 스토어 섹션 */}
      <section className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-lg font-semibold flex items-center gap-2">
              <StoreIcon className="h-5 w-5" />
              마켓플레이스 스토어
            </h2>
            <p className="text-sm text-muted-foreground mt-1">
              주문 수집을 위한 스토어를 관리합니다
            </p>
          </div>
          <Dialog open={isCreateDialogOpen} onOpenChange={setIsCreateDialogOpen}>
            <DialogTrigger asChild>
              <Button>
                <Plus className="mr-2 h-4 w-4" />
                스토어 추가
              </Button>
            </DialogTrigger>
            <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
              <DialogHeader>
                <DialogTitle>스토어 추가</DialogTitle>
                <DialogDescription>
                  새로운 마켓플레이스 스토어를 추가합니다
                </DialogDescription>
              </DialogHeader>
              <form onSubmit={handleCreateStore} className="space-y-6">
                {/* 기본 정보 섹션 */}
                <section>
                  <h3 className="text-lg font-semibold mb-4">기본 정보</h3>
                  <div className="space-y-4">
                    <div className="space-y-2">
                      <Label htmlFor="storeName">스토어 이름 *</Label>
                      <Input
                        id="storeName"
                        name="storeName"
                        placeholder="예: 내 스마트스토어"
                        required
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="marketplace">마켓플레이스 *</Label>
                      <Select name="marketplace" required>
                        <SelectTrigger>
                          <SelectValue placeholder="선택하세요" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="NAVER_SMARTSTORE">네이버 스마트스토어</SelectItem>
                          <SelectItem value="COUPANG">쿠팡</SelectItem>
                        </SelectContent>
                      </Select>
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="externalStoreId">외부 스토어 ID (선택)</Label>
                      <Input
                        id="externalStoreId"
                        name="externalStoreId"
                        placeholder="마켓플레이스의 스토어 ID"
                      />
                    </div>
                  </div>
                </section>

                {/* ERP 연동 설정 섹션 */}
                <section className="border-t pt-6">
                  <h3 className="text-lg font-semibold mb-2">ERP 연동 설정</h3>
                  <p className="text-sm text-muted-foreground mb-4">
                    이카운트 ERP에 등록된 품목코드와 거래처코드를 입력하세요.
                  </p>
                  
                  <div className="grid grid-cols-2 gap-4 mb-6">
                    {/* 기본 창고코드 */}
                    <div className="space-y-2">
                      <Label htmlFor="defaultWarehouseCode">창고코드</Label>
                      <Input
                        id="defaultWarehouseCode"
                        name="defaultWarehouseCode"
                        placeholder="예: 100"
                        defaultValue="100"
                      />
                      <p className="text-xs text-muted-foreground">
                        미입력시 기본값 100 사용
                      </p>
                    </div>
                    
                    {/* 기본 거래처코드 */}
                    <div className="space-y-2">
                      <Label htmlFor="defaultCustomerCode">거래처코드 (오픈마켓) *</Label>
                      <Input
                        id="defaultCustomerCode"
                        name="defaultCustomerCode"
                        placeholder="예: 2208162517"
                        required
                      />
                      <p className="text-xs text-muted-foreground">
                        이 스토어의 매출 거래처코드
                      </p>
                    </div>
                  </div>
                </section>

                {/* 품목코드 설정 섹션 */}
                <section className="border-t pt-6">
                  <h3 className="text-lg font-semibold mb-4">품목코드 설정</h3>
                  
                  <div className="space-y-4">
                    {/* 배송비 품목코드 */}
                    <div className="space-y-2">
                      <Label htmlFor="shippingItemCode">배송비 품목코드 *</Label>
                      <Input
                        id="shippingItemCode"
                        name="shippingItemCode"
                        placeholder="예: 00081"
                        required
                      />
                      <p className="text-xs text-muted-foreground">
                        배송비 매출전표에 사용됩니다.
                      </p>
                    </div>
                    
                    {/* 상품판매 수수료 품목코드 */}
                    <div className="space-y-2">
                      <Label htmlFor="commissionItemCode">상품판매 수수료 품목코드 *</Label>
                      <Input
                        id="commissionItemCode"
                        name="commissionItemCode"
                        placeholder="예: COMM001"
                        required
                      />
                      <p className="text-xs text-muted-foreground">
                        상품판매 수수료 전표에 사용됩니다.
                      </p>
                    </div>
                    
                    {/* 배송비 수수료 품목코드 */}
                    <div className="space-y-2">
                      <Label htmlFor="shippingCommissionItemCode">배송비 수수료 품목코드 *</Label>
                      <Input
                        id="shippingCommissionItemCode"
                        name="shippingCommissionItemCode"
                        placeholder="예: COMM002"
                        required
                      />
                      <p className="text-xs text-muted-foreground">
                        배송비 수수료 전표에 사용됩니다.
                      </p>
                    </div>
                  </div>
                </section>

                {/* 안내 메시지 */}
                <div className="rounded-lg bg-muted p-4">
                  <div className="text-sm text-muted-foreground">
                    <p className="font-medium text-foreground mb-2">💡 안내사항</p>
                    <ul className="space-y-1 list-disc list-inside">
                      <li>모든 품목코드는 ERP 품목마스터에 미리 등록되어 있어야 합니다</li>
                      <li>거래처코드는 ERP 거래처 마스터에 등록된 코드를 사용하세요</li>
                      <li>이 설정은 스토어 생성 후에도 수정 가능합니다</li>
                    </ul>
                  </div>
                </div>
                
                <div className="flex justify-end gap-2 pt-6 border-t">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setIsCreateDialogOpen(false)}
                  >
                    취소
                  </Button>
                  <Button type="submit" disabled={createStoreMutation.isPending}>
                    {createStoreMutation.isPending ? '생성 중...' : '생성'}
                  </Button>
                </div>
              </form>
            </DialogContent>
          </Dialog>
        </div>

        {/* 스토어 목록 */}
        {!stores || stores.length === 0 ? (
          <EmptyState
            icon={<StoreIcon className="h-12 w-12" />}
            title="등록된 스토어가 없습니다"
            description="마켓플레이스 스토어를 추가하여 주문을 수집하세요"
          />
        ) : (
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {stores.map((store) => (
              <Card key={store.storeId} className="p-6">
                <div className="space-y-4">
                  {/* 헤더 */}
                  <div className="flex items-start justify-between">
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <StoreIcon className="h-5 w-5 text-muted-foreground" />
                        <h3 className="font-semibold">{store.storeName}</h3>
                      </div>
                      <p className="text-sm text-muted-foreground">
                        {getMarketplaceLabel(store.marketplace)}
                      </p>
                    </div>
                    <Badge variant={store.isActive ? 'default' : 'secondary'}>
                      {store.isActive ? '활성' : '비활성'}
                    </Badge>
                  </div>

                  {/* 정보 */}
                  <div className="space-y-2 text-sm">
                    <div className="flex justify-between items-center">
                      <span className="text-muted-foreground">거래처 코드</span>
                      <span className="font-medium">
                        {store.defaultCustomerCode || store.erpCustomerCode || (
                          <span className="text-muted-foreground">미설정</span>
                        )}
                      </span>
                    </div>
                    <div className="flex justify-between items-center">
                      <span className="text-muted-foreground">창고 코드</span>
                      <span className="font-medium">
                        {store.defaultWarehouseCode || (
                          <span className="text-muted-foreground">미설정</span>
                        )}
                      </span>
                    </div>
                    <div className="flex justify-between items-center">
                      <span className="text-muted-foreground">배송비 품목</span>
                      <span className="font-medium">
                        {store.shippingItemCode || (
                          <span className="text-muted-foreground">미설정</span>
                        )}
                      </span>
                    </div>
                    {store.lastSyncedAt && (
                      <div className="flex justify-between">
                        <span className="text-muted-foreground">마지막 동기화</span>
                        <span>{formatDate(store.lastSyncedAt)}</span>
                      </div>
                    )}
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">생성일</span>
                      <span>{formatDate(store.createdAt)}</span>
                    </div>
                  </div>

                  {/* 액션 버튼 */}
                  <div className="space-y-2">
                    <Button
                      variant="outline"
                      size="sm"
                      className="w-full"
                      onClick={() => openCredentialDialog(store)}
                    >
                      <Settings className="mr-2 h-4 w-4" />
                      API 연동
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      className="w-full"
                      onClick={() => openCustomerCodeDialog(store)}
                    >
                      <Building2 className="mr-2 h-4 w-4" />
                      거래처 설정
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      className="w-full"
                      onClick={() => openCommissionItemsDialog(store)}
                    >
                      <DollarSign className="mr-2 h-4 w-4" />
                      수수료 품목
                    </Button>
                    <div className="flex gap-2">
                      <Button
                        variant="outline"
                        size="sm"
                        className="flex-1"
                        onClick={() => handleToggleStatus(store)}
                        disabled={updateStatusMutation.isPending}
                      >
                        {store.isActive ? (
                          <PowerOff className="h-4 w-4" />
                        ) : (
                          <Power className="h-4 w-4" />
                        )}
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        className="flex-1"
                        onClick={() => handleDeleteStore(store.storeId)}
                        disabled={deleteStoreMutation.isPending}
                      >
                        <Trash2 className="h-4 w-4 text-destructive" />
                      </Button>
                    </div>
                  </div>
                </div>
              </Card>
            ))}
          </div>
        )}
      </section>

      {/* 거래처 코드 설정 다이얼로그 */}
      <Dialog open={isCustomerCodeDialogOpen} onOpenChange={setIsCustomerCodeDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>ERP 거래처 코드 설정</DialogTitle>
            <DialogDescription>
              {customerCodeStore?.storeName}의 ERP 거래처 코드를 입력하세요
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleUpdateCustomerCode} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="erpCustomerCode">거래처 코드</Label>
              <Input
                id="erpCustomerCode"
                name="erpCustomerCode"
                defaultValue={customerCodeStore?.erpCustomerCode || ''}
                placeholder="예: ONLINE_STORE_01"
                required
              />
              <p className="text-xs text-muted-foreground">
                이 스토어의 주문으로 생성되는 전표에 사용될 거래처 코드입니다
              </p>
            </div>

            <div className="rounded-lg bg-muted p-4">
              <div className="text-sm text-muted-foreground">
                <p className="font-medium text-foreground mb-2">💡 안내</p>
                <ul className="space-y-1 list-disc list-inside">
                  <li>거래처 코드는 ERP에 등록된 거래처 코드여야 합니다</li>
                  <li>미설정시 기본 거래처 코드(ONLINE)가 사용됩니다</li>
                  <li>마켓플레이스별로 다른 거래처 코드를 사용할 수 있습니다</li>
                </ul>
              </div>
            </div>

            <div className="flex justify-end gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  setIsCustomerCodeDialogOpen(false);
                  setCustomerCodeStore(null);
                }}
                disabled={updateCustomerCodeMutation.isPending}
              >
                취소
              </Button>
              <Button type="submit" disabled={updateCustomerCodeMutation.isPending}>
                {updateCustomerCodeMutation.isPending ? '저장 중...' : '저장'}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      {/* 수수료 품목 코드 설정 다이얼로그 */}
      <Dialog open={isCommissionItemsDialogOpen} onOpenChange={setIsCommissionItemsDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>품목 코드 설정</DialogTitle>
            <DialogDescription>
              {commissionItemsStore?.storeName}의 배송비 및 수수료 품목 코드를 입력하세요
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleUpdateCommissionItems} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="shippingItemCode">배송비 품목 코드</Label>
              <Input
                id="shippingItemCode"
                name="shippingItemCode"
                defaultValue={commissionItemsStore?.shippingItemCode || ''}
                placeholder="예: 00081"
              />
              <p className="text-xs text-muted-foreground">
                배송비 매출전표에 사용될 품목 코드입니다
              </p>
            </div>

            <div className="space-y-2">
              <Label htmlFor="commissionItemCode">상품판매 수수료 품목 코드</Label>
              <Input
                id="commissionItemCode"
                name="commissionItemCode"
                defaultValue={commissionItemsStore?.commissionItemCode || ''}
                placeholder="예: 01961"
              />
              <p className="text-xs text-muted-foreground">
                상품 판매에 대한 마켓 수수료 전표에 사용될 품목 코드입니다
              </p>
            </div>

            <div className="space-y-2">
              <Label htmlFor="shippingCommissionItemCode">배송비 수수료 품목 코드</Label>
              <Input
                id="shippingCommissionItemCode"
                name="shippingCommissionItemCode"
                defaultValue={commissionItemsStore?.shippingCommissionItemCode || ''}
                placeholder="예: 01962"
              />
              <p className="text-xs text-muted-foreground">
                배송비에 대한 마켓 수수료 전표에 사용될 품목 코드입니다
              </p>
            </div>

            <div className="rounded-lg bg-muted p-4">
              <div className="text-sm text-muted-foreground">
                <p className="font-medium text-foreground mb-2">💡 안내</p>
                <ul className="space-y-1 list-disc list-inside">
                  <li>품목 코드는 ERP 품목 마스터에 먼저 등록되어 있어야 합니다</li>
                  <li>배송비 품목 코드는 배송비 매출전표 생성 시 사용됩니다</li>
                  <li>수수료 품목 코드는 정산 전표 생성 시 자동으로 사용됩니다</li>
                  <li>각 스토어별로 다른 품목 코드를 사용할 수 있습니다</li>
                </ul>
              </div>
            </div>

            <div className="flex justify-end gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  setIsCommissionItemsDialogOpen(false);
                  setCommissionItemsStore(null);
                }}
                disabled={updateCommissionItemsMutation.isPending}
              >
                취소
              </Button>
              <Button type="submit" disabled={updateCommissionItemsMutation.isPending}>
                {updateCommissionItemsMutation.isPending ? '저장 중...' : '저장'}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      {/* Credential 설정 다이얼로그 */}
      <Dialog open={isCredentialDialogOpen} onOpenChange={setIsCredentialDialogOpen}>
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>연동 정보 설정</DialogTitle>
            <DialogDescription>
              {selectedStore?.storeName}({getMarketplaceLabel(selectedStore?.marketplace || '')})의 API 연동 정보를 입력하세요
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleSaveCredential} className="space-y-6">
            {/* 네이버 스마트스토어 */}
            {selectedStore?.marketplace === 'NAVER_SMARTSTORE' && (
              <div className="space-y-4">
                <div className="pb-2 border-b">
                  <h3 className="font-semibold text-sm">필수 정보</h3>
                  <p className="text-xs text-muted-foreground mt-1">
                    네이버 커머스 API 인증에 필요한 정보입니다
                  </p>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="clientId">
                    Client ID
                    <span className="text-destructive ml-1">*</span>
                  </Label>
                  <Input
                    id="clientId"
                    name="clientId"
                    placeholder="네이버 커머스 API Client ID"
                    required
                  />
                  <p className="text-xs text-muted-foreground">
                    네이버 커머스 API 애플리케이션에서 발급받은 Client ID
                  </p>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="clientSecret">
                    Client Secret
                    <span className="text-destructive ml-1">*</span>
                  </Label>
                  <Input
                    id="clientSecret"
                    name="clientSecret"
                    type="password"
                    placeholder="네이버 커머스 API Client Secret"
                    required
                  />
                  <p className="text-xs text-muted-foreground">
                    네이버 커머스 API 애플리케이션에서 발급받은 Client Secret
                  </p>
                </div>

                <div className="rounded-lg bg-muted p-4">
                  <div className="text-sm text-muted-foreground">
                    <p className="font-medium text-foreground mb-2">📌 안내사항</p>
                    <ul className="space-y-1 list-disc list-inside">
                      <li>네이버 커머스 API 센터에서 애플리케이션을 등록해야 합니다</li>
                      <li>스마트스토어센터 &gt; 스토어관리 &gt; 판매자 정보에서 확인 가능</li>
                      <li>모든 정보는 암호화되어 안전하게 저장됩니다</li>
                    </ul>
                  </div>
                </div>
              </div>
            )}

            {/* 쿠팡 */}
            {selectedStore?.marketplace === 'COUPANG' && (
              <div className="space-y-4">
                <div className="pb-2 border-b">
                  <h3 className="font-semibold text-sm">필수 정보</h3>
                  <p className="text-xs text-muted-foreground mt-1">
                    쿠팡 Vendor API 인증에 필요한 정보입니다
                  </p>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="vendorId">
                    Vendor ID
                    <span className="text-destructive ml-1">*</span>
                  </Label>
                  <Input
                    id="vendorId"
                    name="vendorId"
                    placeholder="쿠팡 Vendor ID"
                    required
                  />
                  <p className="text-xs text-muted-foreground">
                    쿠팡 Wing에서 발급받은 Vendor ID
                  </p>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="accessKey">
                    Access Key
                    <span className="text-destructive ml-1">*</span>
                  </Label>
                  <Input
                    id="accessKey"
                    name="accessKey"
                    placeholder="쿠팡 API Access Key"
                    required
                  />
                  <p className="text-xs text-muted-foreground">
                    쿠팡 Wing에서 발급받은 Access Key
                  </p>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="secretKey">
                    Secret Key
                    <span className="text-destructive ml-1">*</span>
                  </Label>
                  <Input
                    id="secretKey"
                    name="secretKey"
                    type="password"
                    placeholder="쿠팡 API Secret Key"
                    required
                  />
                  <p className="text-xs text-muted-foreground">
                    쿠팡 Wing에서 발급받은 Secret Key
                  </p>
                </div>

                <div className="rounded-lg bg-muted p-4">
                  <div className="text-sm text-muted-foreground">
                    <p className="font-medium text-foreground mb-2">📌 안내사항</p>
                    <ul className="space-y-1 list-disc list-inside">
                      <li>쿠팡 Wing 파트너센터에서 API 키를 발급받아야 합니다</li>
                      <li>Wing &gt; 설정 &gt; OpenAPI 설정에서 확인 가능</li>
                      <li>모든 정보는 암호화되어 안전하게 저장됩니다</li>
                    </ul>
                  </div>
                </div>
              </div>
            )}

            <div className="flex justify-end gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => {
                  setIsCredentialDialogOpen(false);
                  setSelectedStore(null);
                }}
                disabled={isMarketplaceCredentialSaving}
              >
                취소
              </Button>
              <Button type="submit" disabled={isMarketplaceCredentialSaving}>
                {isMarketplaceCredentialSaving ? '저장 중...' : '저장'}
              </Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
