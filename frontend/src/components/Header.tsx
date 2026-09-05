import React from 'react';
import { Shield, BookOpen, Activity, Terminal } from 'lucide-react';
import { Badge } from './Badge';

interface HeaderProps {
  isBackendHealthy: boolean;
}

export const Header: React.FC<HeaderProps> = ({ isBackendHealthy }) => {
  return (
    <header
      style={{
        backgroundColor: 'var(--bg-secondary)',
        borderBottom: '1px solid var(--border-color)',
        padding: '0.875rem 0',
        position: 'sticky',
        top: 0,
        zIndex: 50,
      }}
    >
      <div
        className="container"
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <div
            style={{
              width: '36px',
              height: '36px',
              borderRadius: '8px',
              background: 'linear-gradient(135deg, #3b82f6, #1d4ed8)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'white',
              boxShadow: 'var(--shadow-sm)',
            }}
          >
            <Shield size={22} />
          </div>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <span style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--text-primary)', letterSpacing: '-0.02em' }}>
                ResolveAI
              </span>
              <Badge variant="primary">Phase 1</Badge>
            </div>
            <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
              Enterprise Incident &amp; Service Management
            </span>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '1.25rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Activity size={16} color={isBackendHealthy ? 'var(--success)' : 'var(--warning)'} />
            <span style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)' }}>
              Backend:
            </span>
            <Badge variant={isBackendHealthy ? 'success' : 'warning'}>
              {isBackendHealthy ? 'Online' : 'Standby / Check'}
            </Badge>
          </div>

          <a
            href="http://localhost:8080/swagger-ui.html"
            target="_blank"
            rel="noreferrer"
            className="btn btn-outline"
            style={{ fontSize: '0.8125rem', padding: '0.4rem 0.75rem' }}
          >
            <BookOpen size={14} />
            OpenAPI Docs
          </a>

          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.35rem',
              fontSize: '0.75rem',
              color: 'var(--text-muted)',
              borderLeft: '1px solid var(--border-color)',
              paddingLeft: '1rem',
            }}
          >
            <Terminal size={14} />
            <span>Java 21 • Spring Boot 3</span>
          </div>
        </div>
      </div>
    </header>
  );
};
