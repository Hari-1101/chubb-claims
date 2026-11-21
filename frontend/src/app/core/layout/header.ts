import { Component, inject } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { catchError, map, of, switchMap, timer } from 'rxjs';
import { ClaimApi } from '../api/claim-api';
import { AuthStore } from '../auth/auth-store';

const NOTIFICATION_POLL_MS = 15_000;

/** Top bar: navigation for the current role, who is logged in, and "Switch user". */
@Component({
  selector: 'app-header',
  imports: [RouterLink, RouterLinkActive],
  template: `
    <header class="app-header">
      <div class="container header-row">
        <a class="brand" routerLink="/">Chubb APAC Claims</a>
        @if (auth.user(); as user) {
          <nav>
            @if (auth.isClaimant()) {
              <a routerLink="/claims" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">My claims</a>
              <a routerLink="/claims/new" routerLinkActive="active">New claim</a>
              <a routerLink="/notifications" routerLinkActive="active">
                Notifications
                @if (notificationCount(); as count) {
                  <span class="count">{{ count }}</span>
                }
              </a>
            } @else {
              <a routerLink="/staff/queue" routerLinkActive="active">Work queue</a>
              <a routerLink="/staff/dashboard" routerLinkActive="active">Dashboard</a>
            }
          </nav>
          <div class="who">
            <span>{{ user.name }} <span class="muted">({{ user.id }} · {{ user.team ?? 'Claimant' }})</span></span>
            <button type="button" class="secondary small" (click)="switchUser()">Switch user</button>
          </div>
        }
      </div>
    </header>
  `,
})
export class Header {
  protected readonly auth = inject(AuthStore);
  private readonly claimApi = inject(ClaimApi);
  private readonly router = inject(Router);

  /** Number of notifications for a claimant, refreshed every 15 seconds (no read/unread tracking). */
  protected readonly notificationCount = toSignal(
    toObservable(this.auth.user).pipe(
      switchMap((user) =>
        user?.role === 'CLAIMANT'
          ? timer(0, NOTIFICATION_POLL_MS).pipe(
              switchMap(() =>
                this.claimApi.myNotifications(true).pipe(
                  map((notifications) => notifications.length),
                  catchError(() => of(null)),
                ),
              ),
            )
          : of(null),
      ),
    ),
    { initialValue: null },
  );

  protected switchUser(): void {
    this.auth.logout();
    void this.router.navigateByUrl('/login');
  }
}
