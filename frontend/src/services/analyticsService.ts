import { apiClient } from '../api/client';
import { ApiResponse } from '../types/api';
import {
  AnalyticsOverviewResponse,
  StatusCountResponse,
  PriorityCountResponse,
  SeverityCountResponse,
  TeamWorkloadResponse,
  SlaAnalyticsResponse,
  KnowledgeAnalyticsResponse,
} from '../types/analytics';

export const analyticsService = {
  async getOverview(from?: string, to?: string): Promise<AnalyticsOverviewResponse> {
    const params: Record<string, string> = {};
    if (from) params.from = from;
    if (to) params.to = to;
    const response = await apiClient.get<ApiResponse<AnalyticsOverviewResponse>>('/analytics/overview', { params });
    return response.data.data;
  },

  async getStatusBreakdown(from?: string, to?: string): Promise<StatusCountResponse[]> {
    const params: Record<string, string> = {};
    if (from) params.from = from;
    if (to) params.to = to;
    const response = await apiClient.get<ApiResponse<StatusCountResponse[]>>('/analytics/incidents/status', { params });
    return response.data.data;
  },

  async getPriorityBreakdown(from?: string, to?: string): Promise<PriorityCountResponse[]> {
    const params: Record<string, string> = {};
    if (from) params.from = from;
    if (to) params.to = to;
    const response = await apiClient.get<ApiResponse<PriorityCountResponse[]>>('/analytics/incidents/priority', { params });
    return response.data.data;
  },

  async getSeverityBreakdown(from?: string, to?: string): Promise<SeverityCountResponse[]> {
    const params: Record<string, string> = {};
    if (from) params.from = from;
    if (to) params.to = to;
    const response = await apiClient.get<ApiResponse<SeverityCountResponse[]>>('/analytics/incidents/severity', { params });
    return response.data.data;
  },

  async getTeamWorkload(): Promise<TeamWorkloadResponse[]> {
    const response = await apiClient.get<ApiResponse<TeamWorkloadResponse[]>>('/analytics/teams/workload');
    return response.data.data;
  },

  async getSlaMetrics(): Promise<SlaAnalyticsResponse> {
    const response = await apiClient.get<ApiResponse<SlaAnalyticsResponse>>('/analytics/sla');
    return response.data.data;
  },

  async getKnowledgeMetrics(): Promise<KnowledgeAnalyticsResponse> {
    const response = await apiClient.get<ApiResponse<KnowledgeAnalyticsResponse>>('/analytics/knowledge');
    return response.data.data;
  },
};
