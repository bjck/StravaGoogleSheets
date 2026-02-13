import { clsx } from 'clsx';
import React from 'react';

type Props = {
  children: React.ReactNode;
  tone?: 'success' | 'warn' | 'muted';
};

export function Badge({ children, tone = 'muted' }: Props) {
  return (
    <span
      className={clsx(
        'inline-flex items-center gap-1 rounded-full px-3 py-1 text-xs font-semibold',
        {
          success: 'bg-emerald-500/15 text-emerald-200 border border-emerald-500/30',
          warn: 'bg-amber-500/18 text-amber-100 border border-amber-500/35',
          muted: 'bg-white/8 text-sand border border-white/10',
        }[tone],
      )}
    >
      {children}
    </span>
  );
}

