import React, { useState } from 'react';
import { Card } from './Card';
import { Badge } from './Badge';
import { aiService } from '../services/aiService';
import { IncidentAnalysisResponse } from '../types/ai';
import { Sparkles, Loader2, AlertCircle, CheckCircle, Info } from 'lucide-react';

interface IncidentAiAssistantProps {
  incidentId: number;
}

export const IncidentAiAssistant: React.FC<IncidentAiAssistantProps> = ({ incidentId }) => {
  const [analysis, setAnalysis] = useState<IncidentAnalysisResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleAnalyze = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await aiService.analyzeIncident(incidentId);
      setAnalysis(data);
    } catch (err: any) {
      if (err.response?.status === 503) {
        setError('AI Assistant is currently unavailable or disabled. Core incident management workflows remain fully functional.');
      } else if (err.response?.status === 403) {
        setError('Access denied: You do not have permission to run AI analysis on this incident.');
      } else if (err.response?.status === 404) {
        setError('Incident not found.');
      } else {
        setError(err.response?.data?.message || 'Failed to complete AI analysis. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  const getPriorityVariant = (priority: string) => {
    switch (priority) {
      case 'P1':
        return 'danger';
      case 'P2':
        return 'warning';
      case 'P3':
        return 'primary';
      default:
        return 'neutral';
    }
  };

  const getSeverityVariant = (severity: string) => {
    switch (severity) {
      case 'CRITICAL':
        return 'danger';
      case 'HIGH':
        return 'warning';
      case 'MEDIUM':
        return 'primary';
      default:
        return 'neutral';
    }
  };

  return (
    <Card
      title="AI Incident Advisory"
      subtitle="Automated categorization, severity assessment, and operational triage recommendations"
      headerAction={
        <Badge variant="primary">
          <Sparkles size={12} style={{ marginRight: '0.25rem' }} />
          Advisory Only
        </Badge>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
        {/* Action Bar */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.875rem', color: 'var(--text-secondary)' }}>
            <Info size={16} color="var(--primary)" />
            <span>AI suggestions are non-binding recommendations and do not automatically alter ticket state.</span>
          </div>

          <button
            onClick={handleAnalyze}
            disabled={loading}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.5rem',
              padding: '0.5rem 1.25rem',
              borderRadius: 'var(--radius-md)',
              border: 'none',
              background: 'linear-gradient(135deg, #3b82f6, #2563eb)',
              color: '#ffffff',
              fontWeight: 600,
              fontSize: '0.875rem',
              cursor: loading ? 'not-allowed' : 'pointer',
              opacity: loading ? 0.7 : 1,
              boxShadow: '0 2px 4px rgba(59, 130, 246, 0.3)',
            }}
          >
            {loading ? (
              <>
                <Loader2 size={16} className="animate-spin" />
                Analyzing Incident...
              </>
            ) : (
              <>
                <Sparkles size={16} />
                Analyze with AI
              </>
            )}
          </button>
        </div>

        {/* Error State */}
        {error && (
          <div
            style={{
              padding: '1rem',
              borderRadius: 'var(--radius-sm)',
              backgroundColor: 'rgba(239, 68, 68, 0.1)',
              border: '1px solid rgba(239, 68, 68, 0.3)',
              color: 'var(--danger)',
              display: 'flex',
              alignItems: 'flex-start',
              gap: '0.75rem',
              fontSize: '0.875rem',
            }}
          >
            <AlertCircle size={18} style={{ flexShrink: 0, marginTop: '0.1rem' }} />
            <div>
              <div style={{ fontWeight: 600 }}>AI Analysis Notice</div>
              <div style={{ color: 'var(--text-secondary)', marginTop: '0.25rem' }}>{error}</div>
            </div>
          </div>
        )}

        {/* Results State */}
        {analysis && !loading && (
          <div
            style={{
              backgroundColor: 'rgba(15, 23, 42, 0.6)',
              border: '1px solid var(--border-color)',
              borderRadius: 'var(--radius-sm)',
              padding: '1.25rem',
              display: 'flex',
              flexDirection: 'column',
              gap: '1rem',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid rgba(255,255,255,0.05)', paddingBottom: '0.75rem' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <CheckCircle size={18} color="var(--success)" />
                <span style={{ fontWeight: 700, fontSize: '0.875rem', letterSpacing: '0.05em', color: 'var(--text-primary)' }}>
                  AI SUGGESTION
                </span>
              </div>
              <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                Incident ID: #{analysis.incidentId}
              </span>
            </div>

            {/* Structured Classification Badges */}
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem' }}>
              <div>
                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginBottom: '0.25rem' }}>Suggested Category</div>
                <Badge variant="neutral">{analysis.suggestedCategory}</Badge>
              </div>
              <div>
                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginBottom: '0.25rem' }}>Suggested Priority</div>
                <Badge variant={getPriorityVariant(analysis.suggestedPriority)}>
                  {analysis.suggestedPriority}
                </Badge>
              </div>
              <div>
                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginBottom: '0.25rem' }}>Suggested Severity</div>
                <Badge variant={getSeverityVariant(analysis.suggestedSeverity)}>
                  {analysis.suggestedSeverity}
                </Badge>
              </div>
            </div>

            {/* Summary */}
            <div>
              <div style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '0.25rem' }}>
                Executive Summary
              </div>
              <div style={{ fontSize: '0.875rem', color: 'var(--text-primary)', lineHeight: 1.5 }}>
                {analysis.summary}
              </div>
            </div>

            {/* Operational Analysis */}
            <div>
              <div style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-muted)', textTransform: 'uppercase', marginBottom: '0.25rem' }}>
                Operational Analysis &amp; Suggestions
              </div>
              <div style={{ fontSize: '0.875rem', color: 'var(--text-secondary)', lineHeight: 1.6, whiteSpace: 'pre-line' }}>
                {analysis.analysis}
              </div>
            </div>
          </div>
        )}
      </div>
    </Card>
  );
};
