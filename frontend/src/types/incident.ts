export type IncidentStatus = 
  | 'NEW' 
  | 'TRIAGED' 
  | 'ASSIGNED' 
  | 'IN_PROGRESS' 
  | 'ESCALATED' 
  | 'RESOLVED' 
  | 'REOPENED' 
  | 'CLOSED';

export type Priority = 'P1' | 'P2' | 'P3' | 'P4';

export type Severity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export interface IncidentSummary {
  id: number;
  incidentNumber: string;
  title: string;
  status: IncidentStatus;
  priority: Priority;
  severity: Severity;
  categoryName?: string;
  reporterName: string;
  assigneeName?: string;
  createdAt: string;
  updatedAt: string;
}
