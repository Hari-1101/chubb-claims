import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ClaimApi } from '../core/api/claim-api';
import { ClaimNotification } from '../core/models/api.models';

@Component({
  selector: 'app-notifications',
  imports: [DatePipe, RouterLink],
  template: `
    <h1>Notifications</h1>
    <p class="muted small">Notifications are created from claim events, so a new one can take a moment to appear.</p>
    @if (notifications(); as notifications) {
      @for (n of notifications; track n.id) {
        <div class="card notification">
          <div>{{ n.message }}</div>
          <div class="muted small">
            {{ n.createdAt | date: 'medium' }} ·
            <a [routerLink]="['/claims', n.claimNumber]">{{ n.claimNumber }}</a>
          </div>
        </div>
      } @empty {
        <div class="card empty">No notifications yet.</div>
      }
    } @else {
      <p class="muted">Loading…</p>
    }
  `,
})
export class Notifications {
  private readonly claimApi = inject(ClaimApi);

  /** Newest first (the backend sorts them). */
  protected readonly notifications = signal<ClaimNotification[] | null>(null);

  constructor() {
    this.claimApi.myNotifications().subscribe((list) => this.notifications.set(list));
  }
}
