import React from 'react';
import { Card } from '../components/Card';
import { Badge } from '../components/Badge';
import { HealthCheckResponse } from '../types/api';
import { 
  Server, 
  Layers, 
  Cpu, 
  Database, 
  ArrowRight,
  CheckCircle2,
  Clock
} from 'lucide-react';

interface HomePageProps {
  healthData: HealthCheckResponse | null;
  loading: boolean;
  error: string | null;
}

export const HomePage: React.FC<HomePageProps> = ({ healthData, loading, error }) => {
  const modules = [
    { name: 'common', description: 'Cross-cutting configs, DTOs & Global Exceptions', phase: 'Phase 1', status: 'ready' },
    { name: 'auth', description: 'JWT authentication, tokens & security principal', phase: 'Phase 3', status: 'scaffolded' },
    { name: 'user', description: 'Profiles, RBAC roles & account lifecycle', phase: 'Phase 4', status: 'scaffolded' },
    { name: 'team', description: 'Squads, rosters, leads & workload routing', phase: 'Phase 6', status: 'scaffolded' },
    { name: 'incident', description: 'Core ticket state machine & audit history', phase: 'Phase 5', status: 'scaffolded' },
    { name: 'sla', description: 'Configurable policies, deadlines & breach engine', phase: 'Phase 7', status: 'scaffolded' },
    { name: 'notification', description: 'In-app event-driven alert dispatching', phase: 'Phase 8', status: 'scaffolded' },
    { name: 'knowledge', description: 'Articles, symptoms, causes & search index', phase: 'Phase 9', status: 'scaffolded' },
    { name: 'analytics', description: 'Database-derived KPIs, MTTR & SLA compliance', phase: 'Phase 10', status: 'scaffolded' },
    { name: 'ai', description: 'Copilot summarization, triage & RAG advisory', phase: 'Phase 11-12', status: 'scaffolded' },
    { name: 'audit', description: 'Immutable security & operational audit logs', phase: 'Phase 2-4', status: 'scaffolded' },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
      {/* Hero Section */}
      <div
        style={{
          background: 'linear-gradient(135deg, rgba(30, 41, 59, 0.8), rgba(15, 23, 42, 0.9))',
          border: '1px solid var(--border-color)',
          borderRadius: 'var(--radius-lg)',
          padding: '2.5rem',
          boxShadow: 'var(--shadow-lg)',
          position: 'relative',
          overflow: 'hidden',
        }}
      >
        <div style={{ maxWidth: '800px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1rem' }}>
            <Badge variant="primary">Phase 1 Foundation</Badge>
            <span style={{ fontSize: '0.875rem', color: 'var(--text-muted)' }}>Enterprise Architecture Scaffold</span>
          </div>
          <h1 style={{ fontSize: '2.5rem', fontWeight: 800, color: 'var(--text-primary)', letterSpacing: '-0.03em', lineHeight: 1.2 }}>
            ResolveAI Platform Foundation
          </h1>
          <p style={{ fontSize: '1.125rem', color: 'var(--text-secondary)', marginTop: '1rem', lineHeight: 1.6 }}>
            Enterprise Incident &amp; IT Service Management platform engineered with Java 21, Spring Boot 3 modular monolith backend, and React 18 TypeScript frontend.
          </p>
        </div>
      </div>

      {/* Grid: Health Status & Technical Stack */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(350px, 1fr))', gap: '1.5rem' }}>
        {/* Backend Health Card */}
        <Card
          title="Backend Liveness Status"
          subtitle="Real-time connectivity to Spring Boot REST endpoint (/api/health)"
          headerAction={
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Server size={18} color="var(--primary)" />
            </div>
          }
        >
          {loading && (
            <div style={{ padding: '1.5rem 0', display: 'flex', alignItems: 'center', gap: '0.75rem', color: 'var(--text-secondary)' }}>
              <Clock size={18} />
              <span>Verifying backend connectivity...</span>
            </div>
          )}

          {!loading && error && (
            <div style={{ padding: '1rem', borderRadius: 'var(--radius-sm)', backgroundColor: 'var(--warning-light)', border: '1px solid rgba(245, 158, 11, 0.3)', color: 'var(--warning)' }}>
              <div style={{ fontWeight: 600, marginBottom: '0.25rem' }}>Backend Connection Standby</div>
              <div style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)' }}>
                The frontend shell is ready. Start the backend with <code>mvn spring-boot:run -pl backend</code> to establish live REST connection.
              </div>
            </div>
          )}

          {!loading && healthData && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0.5rem 0', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
                <span style={{ color: 'var(--text-muted)' }}>Status:</span>
                <Badge variant="success">{healthData.status}</Badge>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0.5rem 0', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
                <span style={{ color: 'var(--text-muted)' }}>Service:</span>
                <span style={{ fontWeight: 500 }}>{healthData.service}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0.5rem 0', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
                <span style={{ color: 'var(--text-muted)' }}>Version:</span>
                <span style={{ fontFamily: 'monospace' }}>{healthData.version}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0.5rem 0', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
                <span style={{ color: 'var(--text-muted)' }}>Environment:</span>
                <span style={{ textTransform: 'capitalize' }}>{healthData.environment}</span>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', padding: '0.5rem 0' }}>
                <span style={{ color: 'var(--text-muted)' }}>Server UTC:</span>
                <span style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)' }}>{healthData.timestamp}</span>
              </div>
            </div>
          )}
        </Card>

        {/* Architecture Principles Card */}
        <Card
          title="Architectural Constraints"
          subtitle="Enterprise design rules established in Phase 0"
          headerAction={<Layers size={18} color="var(--primary)" />}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', fontSize: '0.875rem' }}>
            <div style={{ display: 'flex', alignItems: 'flex-start', gap: '0.75rem' }}>
              <CheckCircle2 size={16} color="var(--success)" style={{ marginTop: '0.2rem', flexShrink: 0 }} />
              <div>
                <strong style={{ color: 'var(--text-primary)' }}>Modular Monolith:</strong> Single deployable artifact with 11 isolated bounded contexts.
              </div>
            </div>
            <div style={{ display: 'flex', alignItems: 'flex-start', gap: '0.75rem' }}>
              <CheckCircle2 size={16} color="var(--success)" style={{ marginTop: '0.2rem', flexShrink: 0 }} />
              <div>
                <strong style={{ color: 'var(--text-primary)' }}>Layer Invariance:</strong> Strict Controller &rarr; Service &rarr; Repository &rarr; Database flow.
              </div>
            </div>
            <div style={{ display: 'flex', alignItems: 'flex-start', gap: '0.75rem' }}>
              <CheckCircle2 size={16} color="var(--success)" style={{ marginTop: '0.2rem', flexShrink: 0 }} />
              <div>
                <strong style={{ color: 'var(--text-primary)' }}>DTO Encapsulation:</strong> Zero JPA entity leakage through public REST endpoints.
              </div>
            </div>
            <div style={{ display: 'flex', alignItems: 'flex-start', gap: '0.75rem' }}>
              <CheckCircle2 size={16} color="var(--success)" style={{ marginTop: '0.2rem', flexShrink: 0 }} />
              <div>
                <strong style={{ color: 'var(--text-primary)' }}>Decoupled AI Layer:</strong> Abstract advisory interface with graceful degradation fallback.
              </div>
            </div>
          </div>
        </Card>
      </div>

      {/* Modular Domain Packages Grid */}
      <Card
        title="Modular Domain Bounded Contexts"
        subtitle="Current status of the 11 domain modules specified in architecture"
        headerAction={<Cpu size={18} color="var(--primary)" />}
      >
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
            gap: '1rem',
            marginTop: '0.5rem',
          }}
        >
          {modules.map((mod) => (
            <div
              key={mod.name}
              style={{
                backgroundColor: 'rgba(15, 23, 42, 0.6)',
                border: '1px solid var(--border-color)',
                borderRadius: 'var(--radius-sm)',
                padding: '1rem',
                display: 'flex',
                flexDirection: 'column',
                gap: '0.5rem',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontWeight: 600, fontFamily: 'monospace', color: 'var(--primary)' }}>
                  com.resolveai.{mod.name}
                </span>
                <Badge variant={mod.status === 'ready' ? 'success' : 'neutral'}>
                  {mod.phase}
                </Badge>
              </div>
              <p style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)' }}>
                {mod.description}
              </p>
            </div>
          ))}
        </div>
      </Card>

      {/* Next Step Banner */}
      <div
        style={{
          backgroundColor: 'rgba(59, 130, 246, 0.08)',
          border: '1px solid rgba(59, 130, 246, 0.25)',
          borderRadius: 'var(--radius-md)',
          padding: '1.25rem 1.75rem',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: '1rem',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <div
            style={{
              width: '40px',
              height: '40px',
              borderRadius: '8px',
              backgroundColor: 'rgba(59, 130, 246, 0.2)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'var(--primary)',
            }}
          >
            <Database size={20} />
          </div>
          <div>
            <div style={{ fontWeight: 600, color: 'var(--text-primary)' }}>Next Milestone: Phase 2 — Database and Migrations</div>
            <div style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)' }}>
              Implementation of versioned Flyway migrations (V1 through V6), HikariCP pool, and base JPA entities.
            </div>
          </div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: 'var(--primary)', fontWeight: 500, fontSize: '0.875rem' }}>
          <span>Ready for Phase 2</span>
          <ArrowRight size={16} />
        </div>
      </div>
    </div>
  );
};
