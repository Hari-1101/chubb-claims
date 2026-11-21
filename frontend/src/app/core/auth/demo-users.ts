import { Role } from '../models/api.models';

/**
 * DEMO ONLY. Real authentication is out of scope for this project.
 *
 * These are the users seeded by the backend (backend/src/main/resources/db/migration/V2__seed_data.sql).
 * "Logging in" just picks one of them; the app then sends its id and role in the X-User-Id / X-User-Role headers,
 * which is how the backend's fake auth identifies the caller. Keep this list in sync with the seed data.
 */
export interface DemoUser {
  id: string;
  name: string;
  role: Role;
  /** Staff only. */
  team?: string;
}

export const DEMO_USERS: readonly DemoUser[] = [
  { id: 'C001', name: 'Mei Ling Tan', role: 'CLAIMANT' },
  { id: 'C002', name: 'Ahmad Rahman', role: 'CLAIMANT' },
  { id: 'C003', name: 'Somchai Wong', role: 'CLAIMANT' },
  { id: 'C004', name: 'Olivia Hughes', role: 'CLAIMANT' },
  { id: 'S001', name: 'Daniel Lim', role: 'STAFF', team: 'Motor' },
  { id: 'S002', name: 'Priya Nair', role: 'STAFF', team: 'Motor' },
  { id: 'S003', name: 'Kenji Ho', role: 'STAFF', team: 'Property' },
  { id: 'S004', name: 'Sarah Wijaya', role: 'STAFF', team: 'Property' },
];

export function findDemoUser(id: string | null | undefined): DemoUser | undefined {
  return DEMO_USERS.find((u) => u.id === id);
}

/** "Daniel Lim (S001)" for known users, otherwise just the id. */
export function displayUser(id: string | null | undefined): string {
  if (!id) {
    return '—';
  }
  const user = findDemoUser(id);
  return user ? `${user.name} (${user.id})` : id;
}
