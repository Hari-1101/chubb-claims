import { DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { displayUser } from '../core/auth/demo-users';
import { ClaimDetail } from '../core/models/api.models';
import { MoneyPipe } from './money.pipe';
import { StatusBadge } from './status-badge';

/**
 * The main facts of a claim. Reserve and assigned officer are only shown when showStaffFields is set
 * (the backend doesn't even send the reserve to claimants).
 */
@Component({
  selector: 'app-claim-facts',
  imports: [DatePipe, MoneyPipe, StatusBadge],
  template: `
    <dl class="facts">
      <dt>Status</dt>
      <dd><app-status-badge [status]="claim().status" /></dd>
      <dt>Policy</dt>
      <dd>{{ claim().policyNumber }}</dd>
      <dt>Type / market</dt>
      <dd>{{ claim().type }} · {{ claim().market }}</dd>
      <dt>Incident date</dt>
      <dd>{{ claim().incidentDate | date: 'mediumDate' }}</dd>
      <dt>Estimated amount</dt>
      <dd>{{ claim().estimatedAmount | money: claim().currency }}</dd>
      @if (showStaffFields()) {
        <dt>Reserve</dt>
        <dd><strong>{{ claim().reserveAmount | money: claim().currency }}</strong></dd>
        <dt>Assigned officer</dt>
        <dd>{{ who(claim().assignedOfficerId) }}</dd>
      }
      <dt>Submitted</dt>
      <dd>{{ claim().createdAt | date: 'medium' }}</dd>
      <dt>Description</dt>
      <dd class="pre">{{ claim().description }}</dd>
    </dl>
  `,
})
export class ClaimFacts {
  readonly claim = input.required<ClaimDetail>();
  readonly showStaffFields = input(false);
  protected readonly who = displayUser;
}
