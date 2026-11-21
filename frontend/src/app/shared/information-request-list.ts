import { DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { displayUser } from '../core/auth/demo-users';
import { InformationRequest } from '../core/models/api.models';

/** Questions from the claims officer and the claimant's answers. */
@Component({
  selector: 'app-information-request-list',
  imports: [DatePipe],
  template: `
    @for (request of requests(); track request.id) {
      <div class="info-request">
        <div><strong>Q:</strong> {{ request.question }}</div>
        <div class="muted small">Asked {{ request.requestedAt | date: 'medium' }} by {{ who(request.requestedBy) }}</div>
        @if (request.response) {
          <div><strong>A:</strong> {{ request.response }}</div>
          <div class="muted small">Answered {{ request.respondedAt | date: 'medium' }}</div>
        } @else {
          <div class="pending">Waiting for the claimant's answer</div>
        }
      </div>
    } @empty {
      <p class="muted">No information requests.</p>
    }
  `,
})
export class InformationRequestList {
  readonly requests = input.required<InformationRequest[]>();
  protected readonly who = displayUser;
}
