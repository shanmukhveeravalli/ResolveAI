import React from 'react';
import { MainLayout } from './layouts/MainLayout';
import { HomePage } from './pages/HomePage';
import { useHealthCheck } from './hooks/useHealthCheck';

export const App: React.FC = () => {
  const { data: healthData, loading, error } = useHealthCheck();
  const isBackendHealthy = Boolean(healthData && healthData.status === 'UP');

  return (
    <MainLayout isBackendHealthy={isBackendHealthy}>
      <HomePage healthData={healthData} loading={loading} error={error} />
    </MainLayout>
  );
};

export default App;
