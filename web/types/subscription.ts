// ============================================
// Subscription Types
// ============================================

export type PlanCode = 'TRIAL' | 'STARTER' | 'GROWTH' | 'BUSINESS' | 'ENTERPRISE';

export type SubscriptionStatusType = 'TRIAL' | 'ACTIVE' | 'PAST_DUE' | 'CANCELED' | 'SUSPENDED';

export type InvoiceStatusType = 'PENDING' | 'PAID' | 'FAILED' | 'REFUNDED';

export interface SubscriptionPlan {
  planId: string;
  planCode: PlanCode;
  name: string;
  monthlyPrice: number;
  orderLimitMin: number;
  orderLimitMax: number | null;
  trialDays: number;
  trialPostingLimit: number;
  displayOrder: number;
}

export interface PaymentMethodInfo {
  paymentMethodId: string;
  cardCompany: string;
  cardNumber: string;
  cardType: string;
  isDefault: boolean;
}

export interface Subscription {
  subscriptionId: string;
  plan: SubscriptionPlan;
  status: SubscriptionStatusType;
  trialStartDate: string | null;
  trialEndDate: string | null;
  currentPeriodStart: string | null;
  currentPeriodEnd: string | null;
  billingAnchorDay: number | null;
  cancelAtPeriodEnd: boolean;
  trialPostingsUsed: number;
  paymentMethod: PaymentMethodInfo | null;
  createdAt: string;
}

export interface Invoice {
  invoiceId: string;
  planName: string;
  amount: number;
  status: InvoiceStatusType;
  billingPeriodStart: string;
  billingPeriodEnd: string;
  paymentKey: string | null;
  paidAt: string | null;
  failedReason: string | null;
  retryCount: number;
  createdAt: string;
}

export interface RegisterCardData {
  authKey: string;
  customerKey: string;
}

export interface UpgradePlanData {
  planCode: string;
}
