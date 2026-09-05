export interface HealthCheckResponse {
  status: 'UP' | 'DOWN';
  service: string;
  version: string;
  environment: string;
  timestamp: string;
}

export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  details?: Array<{
    field: string;
    issue: string;
  }>;
}

export interface ApiResponse<T> {
  timestamp: string;
  status: number;
  message: string;
  data: T;
}
