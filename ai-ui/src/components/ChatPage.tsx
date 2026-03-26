import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useChatMutation, useModels } from '../hooks';
import type { ChatMessageDto } from '../api';
import { Spinner } from './Spinner';
import { Button } from './Button';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

type Message = {
  role: 'user' | 'assistant' | 'error';
  text: string;
  model?: string;
  toolsUsed?: string[];
  attachments?: Attachment[];
};

type Attachment = {
  name: string;
  type: string;
  dataUrl?: string;
};

export function ChatPage() {
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [prompt, setPrompt] = useState('');
  const [includeContext, setIncludeContext] = useState(true);
  const [attachments, setAttachments] = useState<Attachment[]>([]);
  const [isRecording, setIsRecording] = useState(false);
  const [speakingMsgIndex, setSpeakingMsgIndex] = useState<number | null>(null);
  const recognitionRef = useRef<SpeechRecognition | null>(null);

  const { data: models, isLoading: loadingModels } = useModels();
  const [model, setModel] = useState(() => localStorage.getItem('ai:model') || '');
  const initialModel = models?.[0]?.name;
  useEffect(() => {
    if (!model && initialModel) setModel(initialModel);
  }, [initialModel, model]);
  useEffect(() => {
    if (model) localStorage.setItem('ai:model', model);
  }, [model]);

  const chatMutation = useChatMutation();

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const conversationHistory = useMemo((): ChatMessageDto[] => {
    return messages
      .filter((m) => m.role !== 'error')
      .map((m) => ({ role: m.role === 'assistant' ? 'user' : m.role, content: m.text }));
  }, [messages]);

  const send = useCallback(async () => {
    const text = prompt.trim();
    if (!text || !model) return;

    const userMsg: Message = { role: 'user', text, attachments: attachments.length ? [...attachments] : undefined };
    setMessages((prev) => [...prev, userMsg]);
    setPrompt('');
    setAttachments([]);

    const allMessages: ChatMessageDto[] = [
      ...conversationHistory,
      { role: 'user', content: text },
    ];

    // If there are attachments, append info about them
    if (userMsg.attachments?.length) {
      const attachInfo = userMsg.attachments.map((a) => `[Attached: ${a.name} (${a.type})]`).join('\n');
      allMessages[allMessages.length - 1].content += '\n\n' + attachInfo;
    }

    try {
      const res = await chatMutation.mutateAsync({
        messages: allMessages,
        model,
        includeContext,
      });
      setMessages((prev) => [
        ...prev,
        {
          role: 'assistant',
          text: res.text || '(no response)',
          model: res.model,
          toolsUsed: res.toolsUsed,
        },
      ]);
    } catch (e) {
      const errorText = e instanceof Error ? e.message : String(e);
      setMessages((prev) => [...prev, { role: 'error', text: errorText }]);
    }
  }, [prompt, model, includeContext, attachments, conversationHistory, chatMutation]);

  // --- Speech to Text ---
  const toggleRecording = useCallback(() => {
    if (isRecording) {
      recognitionRef.current?.stop();
      setIsRecording(false);
      return;
    }

    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) {
      alert('Speech recognition is not supported in your browser.');
      return;
    }

    const recognition = new SpeechRecognition();
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.lang = 'en-US';

    let finalTranscript = prompt;
    recognition.onresult = (event: SpeechRecognitionEvent) => {
      let interim = '';
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const transcript = event.results[i][0].transcript;
        if (event.results[i].isFinal) {
          finalTranscript += transcript + ' ';
        } else {
          interim += transcript;
        }
      }
      setPrompt(finalTranscript + interim);
    };
    recognition.onerror = () => setIsRecording(false);
    recognition.onend = () => setIsRecording(false);

    recognitionRef.current = recognition;
    recognition.start();
    setIsRecording(true);
  }, [isRecording, prompt]);

  // --- Text to Speech ---
  const speak = useCallback((text: string, index: number) => {
    if (speakingMsgIndex === index) {
      speechSynthesis.cancel();
      setSpeakingMsgIndex(null);
      return;
    }
    speechSynthesis.cancel();
    // Strip markdown for cleaner speech
    const clean = text
      .replace(/```[\s\S]*?```/g, 'code block')
      .replace(/[#*_~`>|]/g, '')
      .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
      .replace(/\n{2,}/g, '. ');
    const utterance = new SpeechSynthesisUtterance(clean);
    utterance.rate = 1.0;
    utterance.onend = () => setSpeakingMsgIndex(null);
    utterance.onerror = () => setSpeakingMsgIndex(null);
    setSpeakingMsgIndex(index);
    speechSynthesis.speak(utterance);
  }, [speakingMsgIndex]);

  // --- File Attachments ---
  const handleFiles = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files) return;
    Array.from(files).forEach((file) => {
      const reader = new FileReader();
      reader.onload = () => {
        setAttachments((prev) => [
          ...prev,
          { name: file.name, type: file.type, dataUrl: reader.result as string },
        ]);
      };
      reader.readAsDataURL(file);
    });
    e.target.value = '';
  }, []);

  const removeAttachment = useCallback((index: number) => {
    setAttachments((prev) => prev.filter((_, i) => i !== index));
  }, []);

  const clearChat = useCallback(() => {
    setMessages([]);
    speechSynthesis.cancel();
    setSpeakingMsgIndex(null);
  }, []);

  const modelOptions = useMemo(() => models || [], [models]);
  const hasSpeechRecognition = typeof window !== 'undefined' &&
    (!!window.SpeechRecognition || !!window.webkitSpeechRecognition);

  return (
    <div className="chat-page">
      {/* Toolbar */}
      <div className="chat-toolbar">
        <div className="toolbar-left">
          <div className="select-wrap">
            <select value={model} onChange={(e) => setModel(e.target.value)} disabled={loadingModels}>
              {loadingModels && <option>Loading...</option>}
              {modelOptions.map((m) => (
                <option key={m.fullName} value={m.name}>
                  {m.displayName || m.name}
                </option>
              ))}
            </select>
            <span className="select-caret">&#9660;</span>
          </div>
          <label className="toggle-label">
            <input
              type="checkbox"
              checked={includeContext}
              onChange={(e) => setIncludeContext(e.target.checked)}
            />
            <span>Use fitness tools</span>
          </label>
        </div>
        <div className="toolbar-right">
          <Button variant="ghost" onClick={clearChat}>
            Clear
          </Button>
        </div>
      </div>

      {/* Messages */}
      <div className="messages-container">
        {messages.length === 0 && (
          <div className="empty-state">
            <div className="empty-icon">&#127939;</div>
            <h2>Ask about your workouts</h2>
            <p>
              I can analyze your Strava activities and Garmin health metrics.
              Try asking about your recent training, recovery, or fitness trends.
            </p>
            <div className="suggestion-chips">
              {[
                'How has my running improved this month?',
                'What does my sleep data look like recently?',
                'Give me a training summary for this week',
                'Am I overtraining based on my data?',
              ].map((s) => (
                <button key={s} className="chip" onClick={() => { setPrompt(s); }}>
                  {s}
                </button>
              ))}
            </div>
          </div>
        )}

        {messages.map((m, i) => (
          <div key={i} className={`message message-${m.role}`}>
            <div className="message-header">
              <span className="message-role">
                {m.role === 'user' ? 'You' : m.role === 'assistant' ? 'AI' : 'Error'}
              </span>
              {m.model && <span className="message-model">{m.model}</span>}
              {m.toolsUsed && m.toolsUsed.length > 0 && (
                <span className="tools-badge" title={m.toolsUsed.join(', ')}>
                  &#128295; {m.toolsUsed.length} tool{m.toolsUsed.length > 1 ? 's' : ''}
                </span>
              )}
              {m.role === 'assistant' && (
                <button
                  className={`icon-btn ${speakingMsgIndex === i ? 'active' : ''}`}
                  onClick={() => speak(m.text, i)}
                  title={speakingMsgIndex === i ? 'Stop speaking' : 'Read aloud'}
                >
                  {speakingMsgIndex === i ? '\u23F9' : '\u{1F50A}'}
                </button>
              )}
            </div>
            {m.attachments && m.attachments.length > 0 && (
              <div className="message-attachments">
                {m.attachments.map((a, ai) => (
                  <span key={ai} className="attachment-pill">
                    &#128206; {a.name}
                  </span>
                ))}
              </div>
            )}
            <div className="message-body">
              {m.role === 'error' ? (
                <div className="error-text">{m.text}</div>
              ) : (
                <ReactMarkdown remarkPlugins={[remarkGfm]}>{m.text}</ReactMarkdown>
              )}
            </div>
          </div>
        ))}

        {chatMutation.isPending && (
          <div className="message message-assistant">
            <div className="message-header">
              <span className="message-role">AI</span>
            </div>
            <div className="message-body thinking">
              <Spinner size={16} />
              <span>Thinking...</span>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Composer */}
      <div className="composer-container">
        {attachments.length > 0 && (
          <div className="attachment-bar">
            {attachments.map((a, i) => (
              <span key={i} className="attachment-pill">
                &#128206; {a.name}
                <button className="attachment-remove" onClick={() => removeAttachment(i)}>
                  &#10005;
                </button>
              </span>
            ))}
          </div>
        )}
        <div className="composer">
          <textarea
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            placeholder="Ask about your training, recovery, or anything..."
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                send();
              }
            }}
            rows={2}
          />
          <div className="composer-actions">
            <div className="composer-left">
              <button
                className="icon-btn"
                onClick={() => fileInputRef.current?.click()}
                title="Attach file"
              >
                &#128206;
              </button>
              <input
                ref={fileInputRef}
                type="file"
                multiple
                hidden
                onChange={handleFiles}
                accept="image/*,.pdf,.csv,.txt,.json"
              />
              {hasSpeechRecognition && (
                <button
                  className={`icon-btn mic-btn ${isRecording ? 'recording' : ''}`}
                  onClick={toggleRecording}
                  title={isRecording ? 'Stop recording' : 'Start voice input'}
                >
                  &#127908;
                </button>
              )}
            </div>
            <Button onClick={send} loading={chatMutation.isPending} disabled={!prompt.trim() || !model}>
              Send
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
