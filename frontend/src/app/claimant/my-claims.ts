import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ClaimApi } from '../core/api/claim-api';
import { ClaimSummary } from '../core/models/api.models';
import { StatusBadge } from '../shared/status-badge';

@Component({
  selector: 'app-my-claims',
  imports: [DatePipe, RouterLink, StatusBadge],
  template: `
    <div class="page-title">
      <h1>My claims</h1>
      <a class="button" routerLink="/claims/new">+ New claim</a>
    </div>

    @if (claims(); as claims) {
      @if (claims.length) {
        <table class="table">
          <thead>
            <tr>
              <th>Claim</th>
              <th>Policy</th>
              <th>Type</th>
              <th>Market</th>
              <th>Status</th>
              <th>Submitted</th>
            </tr>
          </thead>
          <tbody>
            @for (claim of claims; track claim.claimNumber) {
              <tr>
                <td><a [routerLink]="['/claims', claim.claimNumber]">{{ claim.claimNumber }}</a></td>
                <td>{{ claim.policyNumber }}</td>
                <td>{{ claim.type }}</td>
                <td>{{ claim.market }}</td>
                <td><app-status-badge [status]="claim.status" /></td>
                <td>{{ claim.createdAt | date: 'medium' }}</td>
              </tr>
            }
          </tbody>
        </table>
      } @else {
        <div class="card empty">You haven't submitted any claims yet.</div>
      }
    } @else {
      <p class="muted">Loading…</p>
    }
  `,
})
export class MyClaims {
  private readonly claimApi = inject(ClaimApi);

  /** Newest first (the backend already sorts them). null while loading. */
  protected readonly claims = signal<ClaimSummary[] | null>(null);

  constructor() {
    this.claimApi.myClaims().subscribe((claims) => this.claims.set(claims));
  }
}
