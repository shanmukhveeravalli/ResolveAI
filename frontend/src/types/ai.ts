import { Priority, Severity } from './incident';

export interface IncidentAnalysisResponse {
  incidentId: number;
  suggestedCategory: string;
  suggestedPriority: Priority;
  suggestedSeverity: Severity;
  summary: string;
  analysis: string;
}
