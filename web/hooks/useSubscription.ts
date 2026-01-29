'use client';

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { subscriptionApi } from '@/lib/api/subscription';
import type { UpgradePlanData, RegisterCardData } from '@/types/subscription';

export function useSubscriptionPlans() {
  return useQuery({
    queryKey: ['subscriptionPlans'],
    queryFn: async () => {
      const res = await subscriptionApi.getPlans();
      return res.data;
    },
    staleTime: 1000 * 60 * 10, // 10분
  });
}

export function useCurrentSubscription() {
  return useQuery({
    queryKey: ['currentSubscription'],
    queryFn: async () => {
      const res = await subscriptionApi.getCurrentSubscription();
      return res.data;
    },
  });
}

export function useInvoices(page: number = 0, size: number = 20) {
  return useQuery({
    queryKey: ['invoices', page, size],
    queryFn: async () => {
      const res = await subscriptionApi.getInvoices(page, size);
      return res.data;
    },
  });
}

export function useUpgradePlan() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: UpgradePlanData) => subscriptionApi.upgradePlan(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['currentSubscription'] });
    },
  });
}

export function useCancelSubscription() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => subscriptionApi.cancelSubscription(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['currentSubscription'] });
    },
  });
}

export function useRegisterCard() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: RegisterCardData) => subscriptionApi.registerCard(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['currentSubscription'] });
    },
  });
}

export function useDeleteCard() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => subscriptionApi.deleteCard(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['currentSubscription'] });
    },
  });
}
