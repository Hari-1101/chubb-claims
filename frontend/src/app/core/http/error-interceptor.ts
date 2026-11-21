import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthStore } from '../auth/auth-store';
import { ProblemDetail } from '../models/api.models';
import { DisplayError, ErrorStore } from './error-store';

/** Set on background requests (polling, auto-refresh) so a failure doesn't pop up the error banner. */
export const SILENT_ERRORS = new HttpContextToken<boolean>(() => false);

/**
 * Shows the backend's ProblemDetail (title, detail, field errors) in the error banner.
 * A 401 means the demo identity is not accepted, so we go back to the login page.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const errors = inject(ErrorStore);
  const auth = inject(AuthStore);
  const router = inject(Router);

  if (req.method !== 'GET') {
    errors.clear(); // a new action starts with a clean banner
  }
  return next(req).pipe(
    catchError((response: HttpErrorResponse) => {
      if (!req.context.get(SILENT_ERRORS)) {
        errors.show(toDisplayError(response));
      }
      if (response.status === 401) {
        auth.logout();
        void router.navigateByUrl('/login');
      }
      return throwError(() => response);
    }),
  );
};

export function toDisplayError(response: HttpErrorResponse): DisplayError {
  if (response.status === 0) {
    return { title: 'Cannot reach the server', detail: 'Is the backend running on port 8080?', fieldErrors: [] };
  }
  const problem = (typeof response.error === 'object' ? response.error : null) as ProblemDetail | null;
  return {
    title: problem?.title ?? `Request failed (${response.status})`,
    detail: problem?.detail ?? response.message,
    fieldErrors: problem?.errors ?? [],
  };
}
