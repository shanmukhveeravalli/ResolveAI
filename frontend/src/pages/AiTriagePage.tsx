import React, { useState } from 'react';
import { Card } from '../components/Card';
import { IncidentAiAssistant } from '../components/IncidentAiAssistant';
import { Hash, ShieldCheck } from 'lucide-react';

export const AiTriagePage: React.FC = () => {
  const [incidentIdInput, setIncidentIdInput] = useState<string>('1');
  const [selectedIncidentId, setSelectedIncidentId] = useState<number>(1);

  const handleApplyIncidentId = (e: React.FormEvent) => {
    e.preventDefault();
    const parsed = parseInt(incidentIdInput, 10);
    if (!isNaN(parsed) && parsed > 0) {
      setSelectedIncidentId(parsed);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      {/* Overview Card */}
      <Card
        title="Phase 10 — AI Foundation & Operational Triage"
        subtitle="Vendor-agnostic AI copilot providing non-binding classification, severity assessment, and incident summarization."
        headerAction={
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', color: 'var(--primary)', fontSize: '0.875rem' }}>
            <ShieldCheck size={16} />
            <span>Human-in-the-Loop Enforced</span>
          </div>
        }
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', flexWrap: 'wrap' }}>
          <form onSubmit={handleApplyIncidentId} style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <label htmlFor="incident-id-input" style={{ fontSize: '0.875rem', fontWeight: 600, color: 'var(--text-secondary)' }}>
              Select Incident ID:
            </label>
            <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
              <Hash size={14} style={{ position: 'absolute', left: '0.75rem', color: 'var(--text-muted)' }} />
              <input
                id="incident-id-input"
                type="number"
                min="1"
                value={incidentIdInput}
                onChange={(e) => setIncidentIdInput(e.target.value)}
                style={{
                  padding: '0.4rem 0.75rem 0.4rem 2rem',
                  borderRadius: 'var(--radius-sm)',
                  border: '1px solid var(--border-color)',
                  backgroundColor: 'rgba(15, 23, 42, 0.8)',
                  color: 'var(--text-primary)',
                  fontSize: '0.875rem',
                  width: '120px',
                }}
              />
            </div>
            <button
              type="submit"
              style={{
                padding: '0.4rem 0.875rem',
                borderRadius: 'var(--radius-sm)',
                border: '1px solid var(--border-color)',
                backgroundColor: 'rgba(59, 130, 246, 0.2)',
                color: 'var(--primary)',
                fontWeight: 600,
                fontSize: '0.875rem',
                cursor: 'pointer',
              }}
            >
              Load Incident
            </button>
          </form>
          <span style={{ fontSize: '0.8125rem', color: 'var(--text-muted)' }}>
            Active Target: Incident #{selectedIncidentId}
          </span>
        </div>
      </Card>

      {/* Incident AI Assistant Panel */}
      <IncidentAiAssistant incidentId={selectedIncidentId} />
    </div>
  );
};
