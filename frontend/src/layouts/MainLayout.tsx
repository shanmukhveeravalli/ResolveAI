import React from 'react';
import { Header } from '../components/Header';
import { Footer } from '../components/Footer';

interface MainLayoutProps {
  children: React.ReactNode;
  isBackendHealthy: boolean;
}

export const MainLayout: React.FC<MainLayoutProps> = ({ children, isBackendHealthy }) => {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      <Header isBackendHealthy={isBackendHealthy} />
      <main style={{ flex: 1, padding: '2.5rem 0' }}>
        <div className="container">{children}</div>
      </main>
      <Footer />
    </div>
  );
};
