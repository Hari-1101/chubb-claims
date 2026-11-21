import { computed, Injectable, signal } from '@angular/core';
import { Role } from '../models/api.models';
import { DemoUser, findDemoUser } from './demo-users';

const STORAGE_KEY = 'claims.demoUser';

/**
 * Who is "logged in" (demo only, see demo-users.ts). Kept in a signal and in sessionStorage, so a page refresh
 * keeps the user but a new browser tab starts at the login page.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly current = signal<DemoUser | null>(restore());

  readonly user = this.current.asReadonly();
  readonly isClaimant = computed(() => this.current()?.role === 'CLAIMANT');
  readonly isStaff = computed(() => this.current()?.role === 'STAFF');

  login(user: DemoUser): void {
    this.current.set(user);
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ id: user.id, role: user.role }));
    } catch {
      // storage unavailable (e.g. private mode): the signal still works for this page
    }
  }

  logout(): void {
    this.current.set(null);
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      // ignore
    }
  }
}

/** Landing page for each role. */
export function homeFor(role: Role): string {
  return role === 'STAFF' ? '/staff/queue' : '/claims';
}

function restore(): DemoUser | null {
  try {
    const saved = JSON.parse(sessionStorage.getItem(STORAGE_KEY) ?? 'null') as { id: string; role: Role } | null;
    const user = findDemoUser(saved?.id);
    return user && user.role === saved?.role ? user : null;
  } catch {
    return null;
  }
}
