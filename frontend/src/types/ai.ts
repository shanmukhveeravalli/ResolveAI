import { Priority, Severity } from './incident';

export interface IncidentAnalysisResponse {
  incidentId: number;
  suggestedCategory: string;
  suggestedPriority: Priority;
  suggestedSeverity: Severity;
  summary: string;
  analysis: string;
}

export type RagSourceType = 'KNOWLEDGE_ARTICLE' | 'HISTORICAL_INCIDENT';

export interface RagSourceDto {
  sourceType: RagSourceType;
  sourceId: number;
  title: string;
  similarity: number;
  reference: string;
  snippet: string;
}

export interface RagAnalysisResponse {
  incidentId: number;
  diagnosis: string;
  resolutionSuggestion: string;
  confidence: string;
  sources: RagSourceDto[];
}
