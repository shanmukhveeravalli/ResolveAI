import React from 'react';

interface BadgeProps {
  children: React.ReactNode;
  variant?: 'primary' | 'success' | 'warning' | 'danger' | 'neutral';
  className?: string;
}

export const Badge: React.FC<BadgeProps> = ({ children, variant = 'neutral', className = '' }) => {
  const getColors = () => {
    switch (variant) {
      case 'primary':
        return { backgroundColor: 'var(--primary-light)', color: 'var(--primary)' };
      case 'success':
        return { backgroundColor: 'var(--success-light)', color: 'var(--success)' };
      case 'warning':
        return { backgroundColor: 'var(--warning-light)', color: 'var(--warning)' };
      case 'danger':
        return { backgroundColor: 'var(--danger-light)', color: 'var(--danger)' };
      default:
        return { backgroundColor: 'rgba(148, 163, 184, 0.15)', color: 'var(--text-secondary)' };
    }
  };

  const style = getColors();

  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        padding: '0.2rem 0.6rem',
        borderRadius: '9999px',
        fontSize: '0.75rem',
        fontWeight: 600,
        letterSpacing: '0.025em',
        textTransform: 'uppercase',
        ...style,
      }}
      className={className}
    >
      {children}
    </span>
  );
};
