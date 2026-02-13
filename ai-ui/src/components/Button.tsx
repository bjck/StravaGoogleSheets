import { clsx } from 'clsx';
import React from 'react';

type Props = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'ghost' | 'outline';
  loading?: boolean;
};

export const Button: React.FC<Props> = ({ variant = 'primary', loading, className, children, ...rest }) => {
  return (
    <button
      {...rest}
      disabled={loading || rest.disabled}
      className={clsx(
        'btn',
        variant === 'primary' && 'btn-primary',
        variant === 'ghost' && 'btn-ghost',
        variant === 'outline' && 'btn-outline',
        className,
      )}
    >
      {loading ? 'Loading…' : children}
    </button>
  );
};
