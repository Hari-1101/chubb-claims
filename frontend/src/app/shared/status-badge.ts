import { Component, computed, input } from '@angular/core';
import { ClaimStatus } from '../core/models/api.models';

const LABELS: Record<ClaimStatus, string> = {
  SUBMITTED: 'Submitted',
  UNDER_REVIEW: 'Under review',
  INFO_REQUESTED: 'Info requested',
  APPROVED: 'Approved',
  SETTLED: 'Settled',
  REJECTED: 'Rejected',
};

export function statusLabel(status: ClaimStatus | null | undefined): string {
  return status ? LABELS[status] : '—';
}

/** Coloured pill for a claim status (colours in styles.scss: .badge-submitted, .badge-under_review, ...). */
@Component({
  selector: 'app-status-badge',
  template: `<span [class]="'badge badge-' + status().toLowerCase()">{{ label() }}</span>`,
})
export class StatusBadge {
  readonly status = input.required<ClaimStatus>();
  protected readonly label = computed(() => statusLabel(this.status()));
}
