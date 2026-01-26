'use client';

import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { toast } from 'sonner';
import { apiClient } from '@/lib/api';
import { CARRIER_OPTIONS } from '@/lib/utils/constants';

interface BulkShipmentDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  orderIds: string[];
  onSuccess: () => void;
}

interface ShipmentInput {
  orderId: string;
  carrierCode: string;
  trackingNo: string;
}

export function BulkShipmentDialog({
  open,
  onOpenChange,
  orderIds,
  onSuccess,
}: BulkShipmentDialogProps) {
  const [shipments, setShipments] = useState<ShipmentInput[]>(
    orderIds.map((orderId) => ({
      orderId,
      carrierCode: '',
      trackingNo: '',
    }))
  );

  const bulkShipmentMutation = useMutation({
    mutationFn: async (data: { shipments: ShipmentInput[] }) => {
      const response = await apiClient.post('/shipments/bulk', data);
      return response.data;
    },
    onSuccess: (response: any) => {
      const result = response.data;
      toast.success(`송장 등록 완료: 성공 ${result.success}건, 실패 ${result.failed}건`);
      onSuccess();
      onOpenChange(false);
    },
    onError: (error: any) => {
      toast.error(
        error.response?.data?.error?.message || '송장 등록 중 오류가 발생했습니다'
      );
    },
  });

  const handleCarrierChange = (index: number, value: string) => {
    const newShipments = [...shipments];
    newShipments[index].carrierCode = value;
    setShipments(newShipments);
  };

  const handleTrackingNoChange = (index: number, value: string) => {
    const newShipments = [...shipments];
    newShipments[index].trackingNo = value;
    setShipments(newShipments);
  };

  const handleSubmit = () => {
    // 검증: 모든 필드가 입력되었는지 확인
    const emptyFields = shipments.some(
      (s) => !s.carrierCode || !s.trackingNo.trim()
    );
    if (emptyFields) {
      toast.error('모든 택배사와 송장번호를 입력해주세요');
      return;
    }

    bulkShipmentMutation.mutate({ shipments });
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl max-h-[80vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>송장 일괄 등록</DialogTitle>
          <DialogDescription>
            선택된 {orderIds.length}개 주문의 송장을 등록합니다.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-4">
          {shipments.map((shipment, index) => (
            <div
              key={shipment.orderId}
              className="grid grid-cols-12 gap-4 items-end border-b pb-4 last:border-0"
            >
              <div className="col-span-1">
                <Label className="text-sm text-muted-foreground">
                  {index + 1}
                </Label>
              </div>
              <div className="col-span-5">
                <Label>택배사</Label>
                <Select
                  value={shipment.carrierCode}
                  onValueChange={(value) => handleCarrierChange(index, value)}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="택배사 선택" />
                  </SelectTrigger>
                  <SelectContent>
                    {CARRIER_OPTIONS.map((carrier) => (
                      <SelectItem key={carrier.value} value={carrier.value}>
                        {carrier.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="col-span-6">
                <Label>송장번호</Label>
                <Input
                  placeholder="송장번호 입력"
                  value={shipment.trackingNo}
                  onChange={(e) =>
                    handleTrackingNoChange(index, e.target.value)
                  }
                />
              </div>
            </div>
          ))}
        </div>

        <DialogFooter>
          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={bulkShipmentMutation.isPending}
          >
            취소
          </Button>
          <Button
            onClick={handleSubmit}
            disabled={bulkShipmentMutation.isPending}
          >
            {bulkShipmentMutation.isPending ? '등록 중...' : '등록'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
