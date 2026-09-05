import { Priority, IncidentStatus } from '../types/incident';

export function formatDate(isoString: string): string {
  try {
    const date = new Date(isoString);
    return new Intl.DateTimeFormat('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: true,
    }).format(date);
  } catch {
    return isoString;
  }
}

export function getPriorityColor(priority: Priority): { bg: string; text: string } {
  switch (priority) {
    case 'P1':
      return { bg: 'rgba(239, 68, 68, 0.15)', text: '#ef4444' };
    case 'P2':
      return { bg: 'rgba(249, 115, 22, 0.15)', text: '#f97316' };
    case 'P3':
      return { bg: 'rgba(245, 158, 11, 0.15)', text: '#f59e0b' };
    case 'P4':
      return { bg: 'rgba(59, 130, 246, 0.15)', text: '#3b82f6' };
    default:
      return { bg: 'rgba(148, 163, 184, 0.15)', text: '#94a3b8' };
  }
}

export function getStatusColor(status: IncidentStatus): { bg: string; text: string } {
  switch (status) {
    case 'NEW':
      return { bg: 'rgba(168, 85, 247, 0.15)', text: '#a855f7' };
    case 'TRIAGED':
    case 'ASSIGNED':
      return { bg: 'rgba(59, 130, 246, 0.15)', text: '#3b82f6' };
    case 'IN_PROGRESS':
      return { bg: 'rgba(245, 158, 11, 0.15)', text: '#f59e0b' };
    case 'ESCALATED':
      return { bg: 'rgba(239, 68, 68, 0.15)', text: '#ef4444' };
    case 'RESOLVED':
    case 'CLOSED':
      return { bg: 'rgba(16, 185, 129, 0.15)', text: '#10b981' };
    case 'REOPENED':
      return { bg: 'rgba(236, 72, 153, 0.15)', text: '#ec4899' };
    default:
      return { bg: 'rgba(148, 163, 184, 0.15)', text: '#94a3b8' };
  }
}
