export type UserRole = 'EMPLOYEE' | 'ENGINEER' | 'MANAGER' | 'ADMIN';

export interface UserProfile {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  role: UserRole;
  teamId?: number;
  teamName?: string;
  createdAt: string;
}
