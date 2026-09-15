import { apiClient } from '../api/client';
import { IncidentAnalysisResponse, RagAnalysisResponse } from '../types/ai';

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

  /**
   * Requests grounded AI analysis for an incident using RAG.
   *
   * @param incidentId target incident identifier
   * @returns grounded advisory response with sources
   */
  analyzeIncidentRag: async (incidentId: number): Promise<RagAnalysisResponse> => {
    const response = await apiClient.post<RagAnalysisResponse>(`/ai/incidents/${incidentId}/rag-analyze`);
    return response.data;
  },
};
