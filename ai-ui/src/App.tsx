import React, { useState } from 'react';
import { ChatPage } from './components/ChatPage';
import { Dashboard } from './components/Dashboard';

type Page = 'chat' | 'dashboard';

export default function App() {
  const [page, setPage] = useState<Page>('chat');

  return (
    <div className="app">
      <nav className="nav-bar">
        <div className="nav-brand">FitnessAI</div>
        <div className="nav-links">
          <button
            className={`nav-link ${page === 'chat' ? 'active' : ''}`}
            onClick={() => setPage('chat')}
          >
            Chat
          </button>
          <button
            className={`nav-link ${page === 'dashboard' ? 'active' : ''}`}
            onClick={() => setPage('dashboard')}
          >
            Dashboard
          </button>
        </div>
      </nav>
      <main className="main-content">
        {page === 'chat' ? <ChatPage /> : <Dashboard />}
      </main>
    </div>
  );
}
