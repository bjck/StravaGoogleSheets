const base = import.meta.env.VITE_API_BASE || '';

async function json<T>(input: RequestInfo | URL, init?: RequestInit): Promise<T> {
  const res = await fetch(input, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers || {}),
    },
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || res.statusText);
  }
  return res.json() as Promise<T>;
}

// --- Types ---

export type ChatMessageDto = { role: string; content: string };

export type ChatResponseDto = {
  model: string;
  text: string;
  usedContext: boolean;
  toolsUsed: string[];
};

export type ModelOption = { name: string; displayName: string; fullName: string };

export type WorkoutStats = {
  totalWorkouts: number;
  activityTypes: string[];
  earliestDate: string | null;
  latestDate: string | null;
  summaryByType: TypeSummary[];
  latestGarmin?: {
    date: string;
    bodyBatteryMax: number | null;
    restingHR: number | null;
    vo2Max: number | null;
    sleepScore: number | null;
    weight: number | null;
  };
};

export type TypeSummary = {
  type: string;
  count: number;
  totalDistanceKm?: number;
  totalHours?: number;
};

// --- API calls ---

export async function fetchModels(): Promise<ModelOption[]> {
  return json<ModelOption[]>(`${base}/ai/models`);
}

export async function fetchStats(): Promise<WorkoutStats> {
  return json<WorkoutStats>(`${base}/ai/stats`);
}

export async function sendChat(
  messages: ChatMessageDto[],
  model: string,
  includeContext: boolean,
): Promise<ChatResponseDto> {
  return json<ChatResponseDto>(`${base}/ai/chat`, {
    method: 'POST',
    body: JSON.stringify({ messages, includeContext, model }),
  });
}

export async function triggerSync(target: 'strava' | 'garmin' | 'all'): Promise<unknown> {
  return json(`${base}/api/sync/${target}`, { method: 'POST' });
}
