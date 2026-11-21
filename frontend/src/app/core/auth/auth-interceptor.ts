import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthStore } from './auth-store';

/** Adds the fake-auth headers (X-User-Id, X-User-Role) to every /api call when a demo user is selected. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const user = inject(AuthStore).user();
  if (!user || !req.url.startsWith('/api')) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { 'X-User-Id': user.id, 'X-User-Role': user.role } }));
};
