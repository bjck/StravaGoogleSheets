import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { askGeminiViaMcp, fetchContext, fetchFitnessSummary, fetchModels, fetchTools, sendChat } from './api';

export function useModels() {
  return useQuery({
    queryKey: ['models'],
    queryFn: fetchModels,
  });
}

export function useContextSnapshot() {
  return useQuery({
    queryKey: ['context'],
    queryFn: fetchContext,
  });
}

export function useTools() {
  return useQuery({
    queryKey: ['tools'],
    queryFn: fetchTools,
  });
}

export function useChatMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (vars: { prompt: string; model: string; includeContext: boolean }) =>
      sendChat(vars.prompt, vars.model, vars.includeContext),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['context'] });
    },
  });
}

export function useFitnessSummaryMutation() {
  return useMutation({
    mutationFn: fetchFitnessSummary,
  });
}

export function useAskGeminiViaMcp() {
  return useMutation({
    mutationFn: (vars: { prompt: string; model: string; includeContext: boolean }) =>
      askGeminiViaMcp(vars.prompt, vars.model, vars.includeContext),
  });
}
