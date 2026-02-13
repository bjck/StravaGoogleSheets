import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Badge } from './components/Badge';
import { Button } from './components/Button';
import { Spinner } from './components/Spinner';
import {
  useAskGeminiViaMcp,
  useChatMutation,
  useContextSnapshot,
  useFitnessSummaryMutation,
  useModels,
  useTools,
} from './hooks';
import type { ModelOption } from './api';
import { clsx } from 'clsx';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

type Message = { role: 'user' | 'assistant' | 'error'; text: string; model?: string };

const defaultIncludeContext = true;

function usePersistedModel(defaultModel?: string) {
  const [model, setModel] = useState(() => localStorage.getItem('ai:model') || defaultModel || '');
  useEffect(() => {
    if (model) localStorage.setItem('ai:model', model);
  }, [model]);
  return { model, setModel };
}

export default function App() {
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [prompt, setPrompt] = useState('');
  const [includeContext, setIncludeContext] = useState(defaultIncludeContext);
  const { data: models, isLoading: loadingModels } = useModels();
  const initialModel = models?.[0]?.name;
  const { model, setModel } = usePersistedModel(initialModel);
  useEffect(() => {
    if (!model && initialModel) setModel(initialModel);
  }, [initialModel, model, setModel]);

  const chatMutation = useChatMutation();
  const { data: ctx, isLoading: loadingCtx, refetch: refetchCtx } = useContextSnapshot();
  const { data: tools } = useTools();
  const fitnessSummary = useFitnessSummaryMutation();
  const askMcp = useAskGeminiViaMcp();
  const [toast, setToast] = useState<string | null>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleError = (e: unknown) => {
    const text = e instanceof Error ? e.message : String(e);
    setToast(text);
  };

  const send = async () => {
    if (!prompt.trim() || !model) return;
    const userMsg: Message = { role: 'user', text: prompt };
    setMessages((prev) => [...prev, userMsg]);
    setPrompt('');
    try {
      const res = await chatMutation.mutateAsync({ prompt, model, includeContext });
      setMessages((prev) => [...prev, { role: 'assistant', text: res.text || '(no text)', model: res.model }]);
    } catch (e) {
      handleError(e);
      setMessages((prev) => [...prev, { role: 'error', text: 'Chat failed' }]);
    }
  };

  const runFitnessSummary = async () => {
    try {
      const res = await fitnessSummary.mutateAsync();
      setMessages((prev) => [...prev, { role: 'assistant', text: JSON.stringify(res, null, 2) }]);
    } catch (e) {
      handleError(e);
    }
  };

  const askViaMcp = async () => {
    if (!prompt.trim() || !model) return;
    setMessages((prev) => [...prev, { role: 'user', text: `[MCP] ${prompt}` }]);
    try {
      const res = await askMcp.mutateAsync({ prompt, model, includeContext });
      setMessages((prev) => [...prev, { role: 'assistant', text: JSON.stringify(res, null, 2) }]);
    } catch (e) {
      handleError(e);
    }
  };

  const modelOptions = useMemo<ModelOption[]>(() => models || [], [models]);

  const contextSummary = useMemo(() => {
    if (!ctx) return 'No context loaded';
    const parts = [];
    if (ctx.strava) parts.push('Strava ✓');
    if (ctx.garmin) parts.push('Garmin ✓');
    if (ctx.recovery) parts.push('Recovery ✓');
    if (ctx.messages?.length) parts.push(ctx.messages.join(' | '));
    return parts.join(' · ') || 'Empty';
  }, [ctx]);

  return (
    <div className="page">
      <header className="header">
        <div>
          <h1 className="title">AI Console</h1>
          <p style={{ margin: 0, color: '#c6c0b4' }}>Chat with Gemini + MCP tools over your fitness data.</p>
        </div>
        <div className="row">
          {loadingModels ? (
            <Badge tone="muted">
              <Spinner size={12} /> Loading models
            </Badge>
          ) : (
            <Badge tone="success">{modelOptions.length || 0} models</Badge>
          )}
          <Badge tone="muted" onClick={() => refetchCtx()}>
            Context {loadingCtx ? '…' : 'ready'}
          </Badge>
        </div>
      </header>

      <div className="grid">
        <div className="panel">
          <div className="row" style={{ marginBottom: 10 }}>
            <div className="select-wrap">
              <select
                value={model}
                onChange={(e) => setModel(e.target.value)}
                disabled={loadingModels}
              >
                {modelOptions.map((m) => (
                  <option key={m.fullName} value={m.name}>
                    {m.displayName || m.name}
                  </option>
                ))}
              </select>
              <span className="select-caret">▼</span>
            </div>
            <label className="row" style={{ color: '#c6c0b4' }}>
              <input
                type="checkbox"
                checked={includeContext}
                onChange={(e) => setIncludeContext(e.target.checked)}
                style={{ width: 16, height: 16 }}
              />
              Include context
            </label>
            <Button variant="outline" onClick={() => refetchCtx()} loading={loadingCtx}>
              Refresh context
            </Button>
          </div>

          <div className="chat-box">
            <div className="messages">
              {messages.map((m, i) => (
                <div key={i} className={clsx('msg', m.role === 'error' && 'border-red-400')}>
                  <div className="role">{m.role}{m.model ? ` · ${m.model}` : ''}</div>
                  <div className="prose prose-invert prose-sm">
                    <ReactMarkdown remarkPlugins={[remarkGfm]}>{m.text}</ReactMarkdown>
                  </div>
                </div>
              ))}
              {(chatMutation.isPending || askMcp.isPending) && (
                <div className="msg">
                  <div className="role">assistant</div>
                  <div className="row" style={{ gap: 6 }}>
                    <Spinner />
                    <span>Thinking…</span>
                  </div>
                </div>
              )}
              <div ref={messagesEndRef} />
            </div>
            <div className="composer">
              <textarea
                value={prompt}
                onChange={(e) => setPrompt(e.target.value)}
                placeholder="Ask about your training, recovery, or anything…"
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    send();
                  }
                }}
              />
              <div className="row">
                <Button onClick={send} loading={chatMutation.isPending}>
                  Send to Gemini
                </Button>
                <Button variant="ghost" onClick={askViaMcp} loading={askMcp.isPending}>
                  Ask via MCP
                </Button>
              </div>
            </div>
          </div>
        </div>

        <div className="panel">
          <div className="sidebar-card">
            <h3 className="section-title">Context</h3>
            {loadingCtx ? (
              <Spinner />
            ) : (
              <div style={{ color: '#e8e1d7', fontSize: 14 }}>
                {contextSummary}
                {ctx?.messages?.length ? (
                  <div style={{ marginTop: 6, color: '#c6c0b4', fontSize: 12 }}>{ctx.messages.join(' | ')}</div>
                ) : null}
              </div>
            )}
          </div>

          <div className="sidebar-card">
            <h3 className="section-title">MCP Tools</h3>
            <div className="row">
              <Button variant="outline" onClick={runFitnessSummary} loading={fitnessSummary.isPending}>
                fitness_summary
              </Button>
              <Button variant="outline" onClick={() => refetchCtx()}>
                reload context
              </Button>
            </div>
            <div style={{ marginTop: 8 }}>
              {tools?.map((t) => (
                <span key={t.name} className="pill" title={t.description}>
                  {t.name}
                </span>
              ))}
            </div>
          </div>

          <div className="sidebar-card">
            <h3 className="section-title">Models</h3>
            {loadingModels ? (
              <Spinner />
            ) : (
              <div>
                {modelOptions.map((m) => (
                  <div key={m.name} className="pill">
                    {m.displayName || m.name}
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      {toast && (
        <div className="toast" role="alert" onClick={() => setToast(null)}>
          {toast}
        </div>
      )}
    </div>
  );
}
