import { apiClient } from '../api/client';
import { IncidentAnalysisResponse } from '../types/ai';

export const aiService = {
  /**
   * Requests non-binding AI triage and advisory analysis for an incident.
   *
   * @param incidentId target incident identifier
   * @returns structured advisory response
   */
  analyzeIncident: async (incidentId: number): Promise<IncidentAnalysisResponse> => {
    const response = await apiClient.post<IncidentAnalysisResponse>(`/ai/incidents/${incidentId}/analyze`);
    return response.data;
  },
};
