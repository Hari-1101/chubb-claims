import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { StaffApi } from '../core/api/staff-api';
import { AuthStore } from '../core/auth/auth-store';
import { displayUser } from '../core/auth/demo-users';
import {
  CLAIM_STATUSES,
  ClaimSummary,
  MARKETS,
  Page,
  PRODUCT_TYPES,
  QueueFilters,
} from '../core/models/api.models';
import { MoneyPipe } from '../shared/money.pipe';
import { StatusBadge, statusLabel } from '../shared/status-badge';
import { availableActions } from './claim-actions';

const PAGE_SIZE = 20;

/** Staff work queue: filterable, paginated list of claims. Without a status filter the backend shows open claims. */
@Component({
  selector: 'app-work-queue',
  imports: [DatePipe, FormsModule, RouterLink, MoneyPipe, StatusBadge],
  templateUrl: './work-queue.html',
})
export class WorkQueue {
  private readonly staffApi = inject(StaffApi);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthStore);

  protected readonly statuses = CLAIM_STATUSES;
  protected readonly markets = MARKETS;
  protected readonly types = PRODUCT_TYPES;
  protected readonly statusLabel = statusLabel;
  protected readonly who = displayUser;

  protected filters: QueueFilters = { status: '', market: '', type: '', assigned: 'any' };
  protected readonly page = signal<Page<ClaimSummary> | null>(null);
  protected readonly assigning = signal<string | null>(null);
  private pageNumber = 0;

  constructor() {
    this.load();
  }

  protected applyFilters(): void {
    this.pageNumber = 0;
    this.load();
  }

  protected goTo(pageNumber: number): void {
    this.pageNumber = pageNumber;
    this.load();
  }

  protected canAssign(claim: ClaimSummary): boolean {
    return availableActions(claim, this.auth.user()!.id).includes('assign');
  }

  protected assignToMe(claim: ClaimSummary): void {
    this.assigning.set(claim.claimNumber);
    this.staffApi.assignToMe(claim.claimNumber).subscribe({
      next: (assigned) => void this.router.navigate(['/staff/claims', assigned.claimNumber]),
      error: () => {
        this.assigning.set(null);
        this.load(); // someone else may have picked it up
      },
    });
  }

  private load(): void {
    this.staffApi.queue(this.filters, this.pageNumber, PAGE_SIZE).subscribe((page) => this.page.set(page));
  }
}
