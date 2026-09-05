import { apiClient } from '../api/client';
import { HealthCheckResponse } from '../types/api';

export const healthService = {
  async getHealth(): Promise<HealthCheckResponse> {
    const response = await apiClient.get<HealthCheckResponse>('/health');
    return response.data;
  },
};
