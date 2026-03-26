import React from 'react';

type Props = {
  children: React.ReactNode;
  tone?: 'success' | 'warn' | 'muted';
  onClick?: () => void;
};

const styles: Record<string, React.CSSProperties> = {
  success: {
    background: 'rgba(43, 182, 115, 0.15)',
    color: '#a7f3d0',
    border: '1px solid rgba(43, 182, 115, 0.3)',
  },
  warn: {
    background: 'rgba(245, 158, 11, 0.18)',
    color: '#fef3c7',
    border: '1px solid rgba(245, 158, 11, 0.35)',
  },
  muted: {
    background: 'rgba(255, 255, 255, 0.08)',
    color: '#c6c0b4',
    border: '1px solid rgba(255, 255, 255, 0.1)',
  },
};

export function Badge({ children, tone = 'muted', onClick }: Props) {
  return (
    <span
      onClick={onClick}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
        borderRadius: 999,
        padding: '4px 12px',
        fontSize: 12,
        fontWeight: 600,
        cursor: onClick ? 'pointer' : undefined,
        ...styles[tone],
      }}
    >
      {children}
    </span>
  );
}
