import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  fetchModels,
  fetchStats,
  fetchTools,
  sendChat,
  executeTool,
  triggerSync,
  type ChatMessageDto,
} from './api';

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

export function useTools() {
  return useQuery({
    queryKey: ['tools'],
    queryFn: fetchTools,
  });
}

export function useChatMutation() {
  return useMutation({
    mutationFn: (vars: { messages: ChatMessageDto[]; model: string; includeContext: boolean }) =>
      sendChat(vars.messages, vars.model, vars.includeContext),
  });
}

export function useToolMutation() {
  return useMutation({
    mutationFn: (vars: { toolName: string; args?: Record<string, unknown> }) =>
      executeTool(vars.toolName, vars.args),
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
