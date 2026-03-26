import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchModels, fetchStats, sendChat, triggerSync, type ChatMessageDto } from './api';

export function useModels() {
  return useQuery({
    queryKey: ['models'],
    queryFn: fetchModels,
  });
}

export function useStats() {
  return useQuery({
    queryKey: ['stats'],
    queryFn: fetchStats,
    refetchInterval: 30_000,
  });
}

export function useChatMutation() {
  return useMutation({
    mutationFn: (vars: { messages: ChatMessageDto[]; model: string; includeContext: boolean }) =>
      sendChat(vars.messages, vars.model, vars.includeContext),
  });
}

export function useSyncMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (target: 'strava' | 'garmin' | 'all') => triggerSync(target),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['stats'] });
    },
  });
}
