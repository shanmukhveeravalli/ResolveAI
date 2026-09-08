import React, { useState } from 'react';
import { MainLayout } from './layouts/MainLayout';
import { HomePage } from './pages/HomePage';
import { AnalyticsPage } from './pages/AnalyticsPage';
import { AiTriagePage } from './pages/AiTriagePage';
import { useHealthCheck } from './hooks/useHealthCheck';
import { BarChart3, Home, Sparkles } from 'lucide-react';

export const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'home' | 'analytics' | 'ai'>('home');
  const { data: healthData, loading, error } = useHealthCheck();
  const isBackendHealthy = Boolean(healthData && healthData.status === 'UP');

  return (
    <MainLayout isBackendHealthy={isBackendHealthy}>
      <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1.5rem' }}>
        <button
          onClick={() => setActiveTab('home')}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '0.4rem',
            padding: '0.5rem 1rem',
            borderRadius: 'var(--radius-md)',
            border: activeTab === 'home' ? '1px solid var(--primary)' : '1px solid var(--border-color)',
            backgroundColor: activeTab === 'home' ? 'rgba(59, 130, 246, 0.15)' : 'var(--bg-card)',
            color: activeTab === 'home' ? 'var(--primary)' : 'var(--text-secondary)',
            fontWeight: 600,
            fontSize: '0.875rem',
            cursor: 'pointer',
          }}
        >
          <Home size={16} /> Platform Overview
        </button>
        <button
          onClick={() => setActiveTab('analytics')}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '0.4rem',
            padding: '0.5rem 1rem',
            borderRadius: 'var(--radius-md)',
            border: activeTab === 'analytics' ? '1px solid var(--primary)' : '1px solid var(--border-color)',
            backgroundColor: activeTab === 'analytics' ? 'rgba(59, 130, 246, 0.15)' : 'var(--bg-card)',
            color: activeTab === 'analytics' ? 'var(--primary)' : 'var(--text-secondary)',
            fontWeight: 600,
            fontSize: '0.875rem',
            cursor: 'pointer',
          }}
        >
          <BarChart3 size={16} /> Operations Analytics
        </button>
        <button
          onClick={() => setActiveTab('ai')}
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '0.4rem',
            padding: '0.5rem 1rem',
            borderRadius: 'var(--radius-md)',
            border: activeTab === 'ai' ? '1px solid var(--primary)' : '1px solid var(--border-color)',
            backgroundColor: activeTab === 'ai' ? 'rgba(59, 130, 246, 0.15)' : 'var(--bg-card)',
            color: activeTab === 'ai' ? 'var(--primary)' : 'var(--text-secondary)',
            fontWeight: 600,
            fontSize: '0.875rem',
            cursor: 'pointer',
          }}
        >
          <Sparkles size={16} /> AI Advisory Triage
        </button>
      </div>

      {activeTab === 'home' && <HomePage healthData={healthData} loading={loading} error={error} />}
      {activeTab === 'analytics' && <AnalyticsPage />}
      {activeTab === 'ai' && <AiTriagePage />}
    </MainLayout>
  );
};

export default App;

