import React, { useState, useEffect } from 'react';
import { Card } from '../components/Card';
import { Badge } from '../components/Badge';
import { analyticsService } from '../services/analyticsService';
import {
  AnalyticsOverviewResponse,
  StatusCountResponse,
  PriorityCountResponse,
  SeverityCountResponse,
  TeamWorkloadResponse,
  SlaAnalyticsResponse,
  KnowledgeAnalyticsResponse,
} from '../types/analytics';
import {
  AlertTriangle,
  Filter,
  RefreshCw,
  Loader2,
} from 'lucide-react';

export const AnalyticsPage: React.FC = () => {
  const [overview, setOverview] = useState<AnalyticsOverviewResponse | null>(null);
  const [statusBreakdown, setStatusBreakdown] = useState<StatusCountResponse[]>([]);
  const [priorityBreakdown, setPriorityBreakdown] = useState<PriorityCountResponse[]>([]);
  const [severityBreakdown, setSeverityBreakdown] = useState<SeverityCountResponse[]>([]);
  const [teamWorkload, setTeamWorkload] = useState<TeamWorkloadResponse[]>([]);
  const [slaMetrics, setSlaMetrics] = useState<SlaAnalyticsResponse | null>(null);
  const [knowledgeMetrics, setKnowledgeMetrics] = useState<KnowledgeAnalyticsResponse | null>(null);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');

  const loadData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [ov, st, pr, sv, tw, sla, kb] = await Promise.all([
        analyticsService.getOverview(fromDate || undefined, toDate || undefined),
        analyticsService.getStatusBreakdown(fromDate || undefined, toDate || undefined),
        analyticsService.getPriorityBreakdown(fromDate || undefined, toDate || undefined),
        analyticsService.getSeverityBreakdown(fromDate || undefined, toDate || undefined),
        analyticsService.getTeamWorkload(),
        analyticsService.getSlaMetrics(),
        analyticsService.getKnowledgeMetrics(),
      ]);
      setOverview(ov);
      setStatusBreakdown(st);
      setPriorityBreakdown(pr);
      setSeverityBreakdown(sv);
      setTeamWorkload(tw);
      setSlaMetrics(sla);
      setKnowledgeMetrics(kb);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to fetch analytics data. Ensure manager or admin credentials.';
      setError(message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleFilterSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    loadData();
  };

  const handleReset = () => {
    setFromDate('');
    setToDate('');
    setLoading(true);
    Promise.all([
      analyticsService.getOverview(),
      analyticsService.getStatusBreakdown(),
      analyticsService.getPriorityBreakdown(),
      analyticsService.getSeverityBreakdown(),
      analyticsService.getTeamWorkload(),
      analyticsService.getSlaMetrics(),
      analyticsService.getKnowledgeMetrics(),
    ])
      .then(([ov, st, pr, sv, tw, sla, kb]) => {
        setOverview(ov);
        setStatusBreakdown(st);
        setPriorityBreakdown(pr);
        setSeverityBreakdown(sv);
        setTeamWorkload(tw);
        setSlaMetrics(sla);
        setKnowledgeMetrics(kb);
      })
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Error reloading');
      })
      .finally(() => setLoading(false));
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
      {/* Page Header */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '1rem',
          background: 'linear-gradient(135deg, rgba(30, 41, 59, 0.8), rgba(15, 23, 42, 0.9))',
          border: '1px solid var(--border-color)',
          borderRadius: 'var(--radius-lg)',
          padding: '2rem',
          boxShadow: 'var(--shadow-lg)',
        }}
      >
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '0.5rem' }}>
            <Badge variant="primary">Phase 9 Analytics</Badge>
            <span style={{ fontSize: '0.875rem', color: 'var(--text-muted)' }}>Operational Intelligence</span>
          </div>
          <h1 style={{ fontSize: '2rem', fontWeight: 800, color: 'var(--text-primary)', margin: 0 }}>
            Incident &amp; Platform Operations Analytics
          </h1>
          <p style={{ color: 'var(--text-secondary)', marginTop: '0.5rem', marginBottom: 0 }}>
            Real-time aggregate performance indicators computed on the database engine.
          </p>
        </div>

        {/* Date Filter Bar */}
        <form
          onSubmit={handleFilterSubmit}
          style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <label style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>From:</label>
            <input
              type="date"
              value={fromDate}
              onChange={(e) => setFromDate(e.target.value)}
              style={{
                backgroundColor: 'var(--bg-card)',
                border: '1px solid var(--border-color)',
                borderRadius: 'var(--radius-md)',
                color: 'var(--text-primary)',
                padding: '0.4rem 0.6rem',
                fontSize: '0.875rem',
              }}
            />
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <label style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>To:</label>
            <input
              type="date"
              value={toDate}
              onChange={(e) => setToDate(e.target.value)}
              style={{
                backgroundColor: 'var(--bg-card)',
                border: '1px solid var(--border-color)',
                borderRadius: 'var(--radius-md)',
                color: 'var(--text-primary)',
                padding: '0.4rem 0.6rem',
                fontSize: '0.875rem',
              }}
            />
          </div>
          <button
            type="submit"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.4rem',
              backgroundColor: 'var(--primary)',
              color: 'white',
              border: 'none',
              borderRadius: 'var(--radius-md)',
              padding: '0.45rem 0.9rem',
              fontSize: '0.875rem',
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            <Filter size={15} /> Filter
          </button>
          <button
            type="button"
            onClick={handleReset}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.4rem',
              backgroundColor: 'transparent',
              color: 'var(--text-secondary)',
              border: '1px solid var(--border-color)',
              borderRadius: 'var(--radius-md)',
              padding: '0.45rem 0.9rem',
              fontSize: '0.875rem',
              cursor: 'pointer',
            }}
          >
            <RefreshCw size={15} /> Reset
          </button>
        </form>
      </div>

      {loading && (
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: 'var(--text-secondary)', fontSize: '0.875rem' }}>
          <Loader2 size={16} className="spin" />
          <span>Refreshing analytics data...</span>
        </div>
      )}

      {error && (
        <div
          style={{
            backgroundColor: 'rgba(239, 68, 68, 0.1)',
            border: '1px solid var(--danger)',
            borderRadius: 'var(--radius-md)',
            padding: '1rem 1.5rem',
            color: 'var(--danger)',
            display: 'flex',
            alignItems: 'center',
            gap: '0.75rem',
          }}
        >
          <AlertTriangle size={20} />
          <span>{error}</span>
        </div>
      )}

      {/* 1. Incident Overview KPI Cards */}
      <div>
        <h2 style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--text-primary)', marginBottom: '1rem' }}>
          Incident Overview
        </h2>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: '1rem' }}>
          <Card>
            <div style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', fontWeight: 600 }}>Total Incidents</div>
            <div style={{ fontSize: '2rem', fontWeight: 800, color: 'var(--text-primary)', marginTop: '0.5rem' }}>
              {overview?.totalIncidents ?? 0}
            </div>
          </Card>
          <Card>
            <div style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', fontWeight: 600 }}>Open Incidents</div>
            <div style={{ fontSize: '2rem', fontWeight: 800, color: '#38bdf8', marginTop: '0.5rem' }}>
              {overview?.openIncidents ?? 0}
            </div>
          </Card>
          <Card>
            <div style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', fontWeight: 600 }}>Resolved Incidents</div>
            <div style={{ fontSize: '2rem', fontWeight: 800, color: 'var(--success)', marginTop: '0.5rem' }}>
              {overview?.resolvedIncidents ?? 0}
            </div>
          </Card>
          <Card>
            <div style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', fontWeight: 600 }}>Closed Incidents</div>
            <div style={{ fontSize: '2rem', fontWeight: 800, color: 'var(--text-muted)', marginTop: '0.5rem' }}>
              {overview?.closedIncidents ?? 0}
            </div>
          </Card>
          <Card>
            <div style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', fontWeight: 600 }}>Escalated Incidents</div>
            <div style={{ fontSize: '2rem', fontWeight: 800, color: 'var(--danger)', marginTop: '0.5rem' }}>
              {overview?.escalatedIncidents ?? 0}
            </div>
          </Card>
          <Card>
            <div style={{ color: 'var(--text-secondary)', fontSize: '0.875rem', fontWeight: 600 }}>Reopened Incidents</div>
            <div style={{ fontSize: '2rem', fontWeight: 800, color: 'var(--warning)', marginTop: '0.5rem' }}>
              {overview?.reopenedIncidents ?? 0}
            </div>
          </Card>
        </div>
      </div>

      {/* 2. Breakdowns Grid: Status, Priority, Severity */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '1.5rem' }}>
        {/* Status Breakdown */}
        <Card title="Status Breakdown" subtitle="Counts grouped by IncidentStatus">
          {statusBreakdown.length === 0 ? (
            <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem', margin: 0 }}>No status records found.</p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              {statusBreakdown.map((item) => (
                <div
                  key={item.status}
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    padding: '0.5rem 0.75rem',
                    backgroundColor: 'rgba(15, 23, 42, 0.4)',
                    borderRadius: 'var(--radius-sm)',
                    border: '1px solid var(--border-color)',
                  }}
                >
                  <Badge variant="primary">{item.status}</Badge>
                  <span style={{ fontWeight: 700, color: 'var(--text-primary)' }}>{item.count}</span>
                </div>
              ))}
            </div>
          )}
        </Card>

        {/* Priority Breakdown */}
        <Card title="Priority Breakdown" subtitle="Distribution across P1 through P4">
          {priorityBreakdown.length === 0 ? (
            <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem', margin: 0 }}>No priority records found.</p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              {priorityBreakdown.map((item) => {
                const badgeVariant =
                  item.priority === 'P1'
                    ? 'danger'
                    : item.priority === 'P2'
                    ? 'warning'
                    : item.priority === 'P3'
                    ? 'primary'
                    : 'neutral';
                return (
                  <div
                    key={item.priority}
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      padding: '0.5rem 0.75rem',
                      backgroundColor: 'rgba(15, 23, 42, 0.4)',
                      borderRadius: 'var(--radius-sm)',
                      border: '1px solid var(--border-color)',
                    }}
                  >
                    <Badge variant={badgeVariant}>{item.priority}</Badge>
                    <span style={{ fontWeight: 700, color: 'var(--text-primary)' }}>{item.count}</span>
                  </div>
                );
              })}
            </div>
          )}
        </Card>

        {/* Severity Breakdown */}
        <Card title="Severity Breakdown" subtitle="CRITICAL, HIGH, MEDIUM, LOW">
          {severityBreakdown.length === 0 ? (
            <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem', margin: 0 }}>No severity records found.</p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              {severityBreakdown.map((item) => {
                const badgeVariant =
                  item.severity === 'CRITICAL'
                    ? 'danger'
                    : item.severity === 'HIGH'
                    ? 'warning'
                    : item.severity === 'MEDIUM'
                    ? 'primary'
                    : 'neutral';
                return (
                  <div
                    key={item.severity}
                    style={{
                      display: 'flex',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      padding: '0.5rem 0.75rem',
                      backgroundColor: 'rgba(15, 23, 42, 0.4)',
                      borderRadius: 'var(--radius-sm)',
                      border: '1px solid var(--border-color)',
                    }}
                  >
                    <Badge variant={badgeVariant}>{item.severity}</Badge>
                    <span style={{ fontWeight: 700, color: 'var(--text-primary)' }}>{item.count}</span>
                  </div>
                );
              })}
            </div>
          )}
        </Card>
      </div>

      {/* 3. Team Workload Table */}
      <Card title="Team Workload" subtitle="Assigned open incident counts across configured squads">
        {teamWorkload.length === 0 ? (
          <p style={{ color: 'var(--text-muted)', fontSize: '0.875rem', margin: 0 }}>No teams configured.</p>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid var(--border-color)' }}>
                  <th style={{ padding: '0.75rem 1rem', color: 'var(--text-secondary)', fontSize: '0.875rem' }}>Team ID</th>
                  <th style={{ padding: '0.75rem 1rem', color: 'var(--text-secondary)', fontSize: '0.875rem' }}>Team Name</th>
                  <th style={{ padding: '0.75rem 1rem', color: 'var(--text-secondary)', fontSize: '0.875rem' }}>Assigned Open Incidents</th>
                </tr>
              </thead>
              <tbody>
                {teamWorkload.map((team) => (
                  <tr key={team.teamId} style={{ borderBottom: '1px solid var(--border-color)' }}>
                    <td style={{ padding: '0.75rem 1rem', color: 'var(--text-muted)', fontSize: '0.875rem' }}>
                      #{team.teamId}
                    </td>
                    <td style={{ padding: '0.75rem 1rem', color: 'var(--text-primary)', fontWeight: 600 }}>
                      {team.teamName}
                    </td>
                    <td style={{ padding: '0.75rem 1rem' }}>
                      <Badge variant={team.assignedOpenIncidentCount > 0 ? 'warning' : 'neutral'}>
                        {team.assignedOpenIncidentCount} open
                      </Badge>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {/* 4. SLA & Knowledge Base Analytics Grid */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '1.5rem' }}>
        {/* SLA Metrics */}
        <Card title="SLA Compliance & Breaches" subtitle="SlaRecord compliance tracking">
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '1rem' }}>
            <div
              style={{
                backgroundColor: 'rgba(15, 23, 42, 0.4)',
                padding: '1rem',
                borderRadius: 'var(--radius-sm)',
                border: '1px solid var(--border-color)',
              }}
            >
              <div style={{ color: 'var(--text-secondary)', fontSize: '0.8125rem' }}>Total SLA Records</div>
              <div style={{ fontSize: '1.5rem', fontWeight: 800, color: 'var(--text-primary)', marginTop: '0.25rem' }}>
                {slaMetrics?.totalSlaRecords ?? 0}
              </div>
            </div>
            <div
              style={{
                backgroundColor: 'rgba(15, 23, 42, 0.4)',
                padding: '1rem',
                borderRadius: 'var(--radius-sm)',
                border: '1px solid var(--border-color)',
              }}
            >
              <div style={{ color: 'var(--text-secondary)', fontSize: '0.8125rem' }}>Response Breaches</div>
              <div style={{ fontSize: '1.5rem', fontWeight: 800, color: 'var(--danger)', marginTop: '0.25rem' }}>
                {slaMetrics?.responseBreaches ?? 0}
              </div>
            </div>
            <div
              style={{
                backgroundColor: 'rgba(15, 23, 42, 0.4)',
                padding: '1rem',
                borderRadius: 'var(--radius-sm)',
                border: '1px solid var(--border-color)',
              }}
            >
              <div style={{ color: 'var(--text-secondary)', fontSize: '0.8125rem' }}>Resolution Breaches</div>
              <div style={{ fontSize: '1.5rem', fontWeight: 800, color: 'var(--danger)', marginTop: '0.25rem' }}>
                {slaMetrics?.resolutionBreaches ?? 0}
              </div>
            </div>
            <div
              style={{
                backgroundColor: 'rgba(15, 23, 42, 0.4)',
                padding: '1rem',
                borderRadius: 'var(--radius-sm)',
                border: '1px solid var(--border-color)',
              }}
            >
              <div style={{ color: 'var(--text-secondary)', fontSize: '0.8125rem' }}>Total Breached Records</div>
              <div style={{ fontSize: '1.5rem', fontWeight: 800, color: 'var(--warning)', marginTop: '0.25rem' }}>
                {slaMetrics?.totalBreachedRecords ?? 0}
              </div>
            </div>
          </div>
        </Card>

        {/* Knowledge Base Summary */}
        <Card title="Knowledge Base Summary" subtitle="Article repository lifecycle status">
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '1rem' }}>
            <div
              style={{
                backgroundColor: 'rgba(15, 23, 42, 0.4)',
                padding: '1rem',
                borderRadius: 'var(--radius-sm)',
                border: '1px solid var(--border-color)',
              }}
            >
              <div style={{ color: 'var(--text-secondary)', fontSize: '0.8125rem' }}>Total Articles</div>
              <div style={{ fontSize: '1.5rem', fontWeight: 800, color: 'var(--text-primary)', marginTop: '0.25rem' }}>
                {knowledgeMetrics?.totalArticles ?? 0}
              </div>
            </div>
            <div
              style={{
                backgroundColor: 'rgba(15, 23, 42, 0.4)',
                padding: '1rem',
                borderRadius: 'var(--radius-sm)',
                border: '1px solid var(--border-color)',
              }}
            >
              <div style={{ color: 'var(--text-secondary)', fontSize: '0.8125rem' }}>Published Articles</div>
              <div style={{ fontSize: '1.5rem', fontWeight: 800, color: 'var(--success)', marginTop: '0.25rem' }}>
                {knowledgeMetrics?.publishedArticles ?? 0}
              </div>
            </div>
            <div
              style={{
                backgroundColor: 'rgba(15, 23, 42, 0.4)',
                padding: '1rem',
                borderRadius: 'var(--radius-sm)',
                border: '1px solid var(--border-color)',
              }}
            >
              <div style={{ color: 'var(--text-secondary)', fontSize: '0.8125rem' }}>Draft Articles</div>
              <div style={{ fontSize: '1.5rem', fontWeight: 800, color: '#38bdf8', marginTop: '0.25rem' }}>
                {knowledgeMetrics?.draftArticles ?? 0}
              </div>
            </div>
            <div
              style={{
                backgroundColor: 'rgba(15, 23, 42, 0.4)',
                padding: '1rem',
                borderRadius: 'var(--radius-sm)',
                border: '1px solid var(--border-color)',
              }}
            >
              <div style={{ color: 'var(--text-secondary)', fontSize: '0.8125rem' }}>Archived Articles</div>
              <div style={{ fontSize: '1.5rem', fontWeight: 800, color: 'var(--text-muted)', marginTop: '0.25rem' }}>
                {knowledgeMetrics?.archivedArticles ?? 0}
              </div>
            </div>
          </div>
        </Card>
      </div>
    </div>
  );
};
