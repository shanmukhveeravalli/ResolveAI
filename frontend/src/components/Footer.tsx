import React from 'react';

export const Footer: React.FC = () => {
  return (
    <footer
      style={{
        backgroundColor: 'var(--bg-secondary)',
        borderTop: '1px solid var(--border-color)',
        padding: '1.5rem 0',
        marginTop: 'auto',
      }}
    >
      <div
        className="container"
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '1rem',
          fontSize: '0.8125rem',
          color: 'var(--text-muted)',
        }}
      >
        <div>
          <span>ResolveAI — AI-Powered Enterprise Incident &amp; Service Management Platform</span>
          <span style={{ margin: '0 0.5rem' }}>•</span>
          <span>4th-Year CSE/IT Enterprise Architecture Project</span>
        </div>
        <div style={{ display: 'flex', gap: '1.5rem' }}>
          <span>PostgreSQL 16</span>
          <span>Spring Security</span>
          <span>Flyway</span>
          <span>React + TypeScript + Vite</span>
        </div>
      </div>
    </footer>
  );
};
