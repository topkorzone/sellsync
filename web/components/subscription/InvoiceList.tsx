'use client';

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import type { Invoice, InvoiceStatusType } from '@/types/subscription';

interface InvoiceListProps {
  invoices: Invoice[];
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

const statusConfig: Record<InvoiceStatusType, { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' | 'success' | 'warning' }> = {
  PENDING: { label: '대기', variant: 'outline' },
  PAID: { label: '결제 완료', variant: 'success' },
  FAILED: { label: '결제 실패', variant: 'destructive' },
  REFUNDED: { label: '환불', variant: 'secondary' },
};

const formatDate = (dateStr: string | null) => {
  if (!dateStr) return '-';
  return new Date(dateStr).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
};

const formatPrice = (price: number) => {
  return new Intl.NumberFormat('ko-KR').format(price) + '원';
};

export function InvoiceList({ invoices, page, totalPages, onPageChange }: InvoiceListProps) {
  if (invoices.length === 0) {
    return (
      <div className="text-center py-8 text-muted-foreground">
        청구서가 없습니다.
      </div>
    );
  }

  return (
    <div>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>결제일</TableHead>
            <TableHead>플랜</TableHead>
            <TableHead>구독 기간</TableHead>
            <TableHead className="text-right">금액</TableHead>
            <TableHead>상태</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {invoices.map((invoice) => {
            const config = statusConfig[invoice.status] || { label: invoice.status, variant: 'outline' as const };
            return (
              <TableRow key={invoice.invoiceId}>
                <TableCell>{formatDate(invoice.paidAt || invoice.createdAt)}</TableCell>
                <TableCell>{invoice.planName}</TableCell>
                <TableCell>
                  {formatDate(invoice.billingPeriodStart)} ~ {formatDate(invoice.billingPeriodEnd)}
                </TableCell>
                <TableCell className="text-right font-medium">{formatPrice(invoice.amount)}</TableCell>
                <TableCell>
                  <Badge variant={config.variant}>{config.label}</Badge>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>

      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-2 mt-4">
          <Button
            variant="outline"
            size="sm"
            disabled={page === 0}
            onClick={() => onPageChange(page - 1)}
          >
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="text-sm text-muted-foreground">
            {page + 1} / {totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={page >= totalPages - 1}
            onClick={() => onPageChange(page + 1)}
          >
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      )}
    </div>
  );
}
