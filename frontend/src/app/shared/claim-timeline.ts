import { DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { displayUser } from '../core/auth/demo-users';
import { StatusHistoryEntry } from '../core/models/api.models';
import { StatusBadge } from './status-badge';

/** Status history of a claim: who changed it, when, and why. */
@Component({
  selector: 'app-claim-timeline',
  imports: [DatePipe, StatusBadge],
  template: `
    <ol class="timeline">
      @for (entry of history(); track $index) {
        <li>
          <div><app-status-badge [status]="entry.toStatus" /></div>
          <div class="muted small">{{ entry.changedAt | date: 'medium' }} · by {{ who(entry.changedBy) }}</div>
          @if (entry.reason) {
            <div>{{ entry.reason }}</div>
          }
        </li>
      }
    </ol>
  `,
})
export class ClaimTimeline {
  readonly history = input.required<StatusHistoryEntry[]>();
  protected readonly who = displayUser;
}
