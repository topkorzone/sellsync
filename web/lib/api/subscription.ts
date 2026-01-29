import { apiClient } from './client';
import type { ApiResponse, PaginatedResponse } from '@/types';
import type {
  SubscriptionPlan,
  Subscription,
  Invoice,
  RegisterCardData,
  UpgradePlanData,
} from '@/types/subscription';

export const subscriptionApi = {
  getPlans: async () => {
    const res = await apiClient.get<ApiResponse<SubscriptionPlan[]>>('/subscriptions/plans');
    return res.data;
  },

  getCurrentSubscription: async () => {
    const res = await apiClient.get<ApiResponse<Subscription>>('/subscriptions/current');
    return res.data;
  },

  startSubscription: async () => {
    const res = await apiClient.post<ApiResponse<Subscription>>('/subscriptions');
    return res.data;
  },

  upgradePlan: async (data: UpgradePlanData) => {
    const res = await apiClient.post<ApiResponse<Subscription>>('/subscriptions/upgrade', data);
    return res.data;
  },

  cancelSubscription: async () => {
    const res = await apiClient.post<ApiResponse<Subscription>>('/subscriptions/cancel');
    return res.data;
  },

  registerCard: async (data: RegisterCardData) => {
    const res = await apiClient.post<ApiResponse<{ message: string }>>('/billing/register-card', data);
    return res.data;
  },

  deleteCard: async (id: string) => {
    const res = await apiClient.delete<ApiResponse<{ message: string }>>(`/billing/card/${id}`);
    return res.data;
  },

  getInvoices: async (page: number = 0, size: number = 20) => {
    const res = await apiClient.get<ApiResponse<PaginatedResponse<Invoice>>>('/billing/invoices', {
      params: { page, size },
    });
    return res.data;
  },
};
