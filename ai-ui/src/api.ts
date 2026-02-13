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

export type ChatMessageDto = { role: string; content: string };
export type ChatResponseDto = { model: string; text: string; usedContext: boolean };
export type FitnessContextDto = {
  messages: string[];
  strava?: Record<string, unknown>;
  garmin?: Record<string, unknown>;
  recovery?: Record<string, unknown>;
};

export type ModelOption = { name: string; displayName: string; fullName: string };

export async function fetchModels(): Promise<ModelOption[]> {
  return json<ModelOption[]>(`${base}/ai/models`);
}

export async function fetchContext(): Promise<FitnessContextDto> {
  return json<FitnessContextDto>(`${base}/ai/context`);
}

export async function sendChat(prompt: string, model: string, includeContext: boolean): Promise<ChatResponseDto> {
  return json<ChatResponseDto>(`${base}/ai/chat`, {
    method: 'POST',
    body: JSON.stringify({
      messages: [{ role: 'user', content: prompt } satisfies ChatMessageDto],
      includeContext,
      model,
    }),
  });
}

export type McpTool = { name: string; description: string };

export async function fetchTools(): Promise<McpTool[]> {
  return json<McpTool[]>(`${base}/mcp/tools`);
}

export async function fetchFitnessSummary(): Promise<unknown> {
  return json(`${base}/mcp/tools/fitness_summary`, { method: 'POST' });
}

export async function askGeminiViaMcp(prompt: string, model: string, includeContext: boolean): Promise<unknown> {
  return json(`${base}/mcp/tools/ask_gemini`, {
    method: 'POST',
    body: JSON.stringify({ prompt, model, includeContext }),
  });
}
