import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from '../models/api.models';
import { AuthStore, homeFor } from './auth-store';

/**
 * Only lets users with the given role in. Not logged in → login page; wrong role → their own home page.
 * (The backend enforces the same rules; this just keeps people out of screens that would only show errors.)
 */
export function roleGuard(role: Role): CanActivateFn {
  return () => {
    const user = inject(AuthStore).user();
    const router = inject(Router);
    if (!user) {
      return router.parseUrl('/login');
    }
    return user.role === role ? true : router.parseUrl(homeFor(user.role));
  };
}
