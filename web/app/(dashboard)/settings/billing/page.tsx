'use client';

import { useState } from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { CardRegistrationModal } from '@/components/subscription/CardRegistrationModal';
import { InvoiceList } from '@/components/subscription/InvoiceList';
import { useCurrentSubscription, useInvoices, useDeleteCard } from '@/hooks/useSubscription';
import { useAuthStore } from '@/lib/stores/auth-store';
import { toast } from 'sonner';
import { CreditCard, Plus, Trash2 } from 'lucide-react';

export default function BillingSettingsPage() {
  const { user } = useAuthStore();
  const { data: subscription, isLoading: subLoading } = useCurrentSubscription();
  const [invoicePage, setInvoicePage] = useState(0);
  const { data: invoicesData, isLoading: invoicesLoading } = useInvoices(invoicePage);
  const deleteCard = useDeleteCard();
  const [cardModalOpen, setCardModalOpen] = useState(false);

  const paymentMethod = subscription?.paymentMethod;
  const customerKey = user?.tenantId || '';

  const handleDeleteCard = async () => {
    if (!paymentMethod) return;

    if (!confirm('등록된 카드를 삭제하시겠습니까?')) return;

    try {
      await deleteCard.mutateAsync(paymentMethod.paymentMethodId);
      toast.success('카드가 삭제되었습니다.');
    } catch {
      toast.error('카드 삭제에 실패했습니다.');
    }
  };

  if (subLoading) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="text-muted-foreground">로딩 중...</div>
      </div>
    );
  }

  return (
    <div className="h-full overflow-y-auto space-y-6">
      <div>
        <h1 className="text-2xl font-bold">결제 관리</h1>
        <p className="text-muted-foreground">결제수단과 청구서를 관리합니다.</p>
      </div>

      {/* 결제수단 */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle className="text-lg">결제수단</CardTitle>
              <CardDescription>자동결제에 사용되는 카드 정보</CardDescription>
            </div>
            <Button size="sm" onClick={() => setCardModalOpen(true)}>
              <Plus className="h-4 w-4 mr-1" />
              카드 등록
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          {paymentMethod ? (
            <div className="flex items-center justify-between p-4 border rounded-lg">
              <div className="flex items-center gap-3">
                <CreditCard className="h-8 w-8 text-muted-foreground" />
                <div>
                  <div className="font-medium">
                    {paymentMethod.cardCompany || '카드'} {paymentMethod.cardNumber}
                  </div>
                  <div className="text-sm text-muted-foreground">
                    {paymentMethod.cardType || '신용카드'}
                  </div>
                </div>
              </div>
              <div className="flex items-center gap-2">
                {paymentMethod.isDefault && <Badge variant="secondary">기본</Badge>}
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={handleDeleteCard}
                  disabled={deleteCard.isPending}
                >
                  <Trash2 className="h-4 w-4 text-destructive" />
                </Button>
              </div>
            </div>
          ) : (
            <div className="text-center py-8 text-muted-foreground">
              <CreditCard className="h-10 w-10 mx-auto mb-2 opacity-40" />
              <p>등록된 결제수단이 없습니다.</p>
              <p className="text-sm mt-1">유료 플랜을 이용하려면 카드를 등록해주세요.</p>
            </div>
          )}
        </CardContent>
      </Card>

      {/* 청구서 */}
      <Card>
        <CardHeader>
          <CardTitle className="text-lg">청구서</CardTitle>
          <CardDescription>결제 이력을 확인합니다.</CardDescription>
        </CardHeader>
        <CardContent>
          {invoicesLoading ? (
            <div className="text-center py-8 text-muted-foreground">로딩 중...</div>
          ) : (
            <InvoiceList
              invoices={invoicesData?.items || []}
              page={invoicePage}
              totalPages={invoicesData?.totalPages || 0}
              onPageChange={setInvoicePage}
            />
          )}
        </CardContent>
      </Card>

      {/* 카드 등록 모달 */}
      <CardRegistrationModal
        open={cardModalOpen}
        onOpenChange={setCardModalOpen}
        customerKey={customerKey}
      />
    </div>
  );
}
