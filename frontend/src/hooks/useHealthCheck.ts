import { useState, useEffect } from 'react';
import { healthService } from '../services/healthService';
import { HealthCheckResponse } from '../types/api';

export function useHealthCheck() {
  const [data, setData] = useState<HealthCheckResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    async function fetchHealth() {
      try {
        setLoading(true);
        setError(null);
        const result = await healthService.getHealth();
        if (isMounted) {
          setData(result);
        }
      } catch (err: unknown) {
        if (isMounted) {
          setError(err instanceof Error ? err.message : 'Unable to connect to backend service');
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    }

    fetchHealth();

    return () => {
      isMounted = false;
    };
  }, []);

  return { data, loading, error };
}
