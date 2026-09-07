export interface AnalyticsOverviewResponse {
  totalIncidents: number;
  openIncidents: number;
  resolvedIncidents: number;
  closedIncidents: number;
  escalatedIncidents: number;
  reopenedIncidents: number;
}

export interface StatusCountResponse {
  status: string;
  count: number;
}

export interface PriorityCountResponse {
  priority: string;
  count: number;
}

export interface SeverityCountResponse {
  severity: string;
  count: number;
}

export interface TeamWorkloadResponse {
  teamId: number;
  teamName: string;
  assignedOpenIncidentCount: number;
}

export interface SlaAnalyticsResponse {
  totalSlaRecords: number;
  responseBreaches: number;
  resolutionBreaches: number;
  totalBreachedRecords: number;
}

export interface KnowledgeAnalyticsResponse {
  totalArticles: number;
  publishedArticles: number;
  draftArticles: number;
  archivedArticles: number;
}
