# AI Module (Gemini, non-Vertex)

New Spring Modulith module `ai` adds Gemini chat plus MCP-style tools to fetch fitness context from your Google Sheet. A React SPA at `/ai/console` provides the UI.

## Configuration
- `AI_GEMINI_API_KEY` – API key from Google AI Studio (not Vertex). `GOOGLE_API_KEY` also works.
- `AI_GEMINI_DEFAULT_MODEL` – optional, defaults to `gemini-1.5-flash-latest` (use `gemini-1.5-pro` if your key has access).
- `AI_GEMINI_BASE_URL` – optional, defaults to `https://generativelanguage.googleapis.com/v1beta`.

Example `.env`:
```
AI_GEMINI_API_KEY=your_ai_studio_key
AI_GEMINI_DEFAULT_MODEL=gemini-1.5-flash-latest
```

## Endpoints
- `POST /ai/chat` – body `{ "messages":[{"role":"user","content":"..."}], "includeContext":true }`
- `GET /ai/context` – Strava/Garmin/recovery snapshot for prompting
- `GET /ai/models` – lists available models (displayName + name)
- MCP helpers (HTTP JSON):
  - `GET /mcp/tools`
  - `POST /mcp/tools/fitness_summary`
  - `POST /mcp/tools/ask_gemini` with `{ "prompt": "...", "includeContext": true }`

The AI chat service automatically injects a compact fitness context built from your spreadsheet tabs. No Vertex settings are required.

## Frontend (React SPA)
- Location: `src/main/resources/static/ai/` (built from `ai-ui/` Vite + React + TS + TanStack Query).
- Served at: `http://localhost:8080/ai/console` (controller forwards to SPA).
- Features: model dropdown, context viewer, chat with markdown rendering, inline loader while waiting, MCP tool triggers, include-context toggle, toasts on error, persisted last-used model.

### Build & Run
1) `cd ai-ui`
2) `npm install`
3) `npm run build` (outputs to `src/main/resources/static/ai/`)
4) `mvn spring-boot:run`
5) Open `http://localhost:8080/ai/console`

### Key Frontend Files
- `ai-ui/src/App.tsx` – main UI
- `ai-ui/src/api.ts` / `ai-ui/src/hooks.ts` – API calls and queries
- `ai-ui/src/styles.css` – styling
- `ai-ui/vite.config.ts` – build config (base `/ai/`)

### Key Backend Pieces
- `ModelCatalogService` – calls `GET /models?key=…` and exposes `/ai/models`
- `ChatService` – sanitizes model names and calls Gemini via the official client
- `AiPageController` – forwards `/ai/console` to the SPA
