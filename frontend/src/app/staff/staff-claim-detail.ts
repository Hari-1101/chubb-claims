import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ClaimApi } from '../core/api/claim-api';
import { StaffApi } from '../core/api/staff-api';
import { AuthStore } from '../core/auth/auth-store';
import { ClaimDetail } from '../core/models/api.models';
import { ClaimFacts } from '../shared/claim-facts';
import { ClaimTimeline } from '../shared/claim-timeline';
import { InformationRequestList } from '../shared/information-request-list';
import { money, notBlank } from '../shared/validators';
import { availableActions, StaffAction } from './claim-actions';

/**
 * Staff view of a claim: everything the claimant sees plus reserve and assigned officer, and the actions that are
 * valid right now (status + "I am the assigned officer"). After each action the claim is reloaded.
 */
@Component({
  selector: 'app-staff-claim-detail',
  imports: [ReactiveFormsModule, RouterLink, ClaimFacts, ClaimTimeline, InformationRequestList],
  templateUrl: './staff-claim-detail.html',
})
export class StaffClaimDetail implements OnInit {
  /** From the route (:claimNumber). */
  readonly claimNumber = input.required<string>();

  private readonly claimApi = inject(ClaimApi);
  private readonly staffApi = inject(StaffApi);
  private readonly auth = inject(AuthStore);

  protected readonly claim = signal<ClaimDetail | null>(null);
  protected readonly failed = signal(false);
  protected readonly busy = signal(false);
  /** Which action form is open (null = none). */
  protected readonly openForm = signal<StaffAction | null>(null);

  protected readonly actions = computed(() => {
    const claim = this.claim();
    return claim ? availableActions(claim, this.auth.user()!.id) : [];
  });

  /** Explains why there are no buttons, so an empty action bar isn't confusing. */
  protected readonly noActionsReason = computed(() => {
    const claim = this.claim();
    if (!claim || this.actions().length) return '';
    if (claim.status === 'SETTLED' || claim.status === 'REJECTED') return 'This claim is closed.';
    if (claim.assignedOfficerId !== this.auth.user()!.id) return 'Only the assigned officer can act on this claim.';
    return '';
  });

  protected readonly question = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, notBlank, Validators.maxLength(2000)],
  });
  protected readonly reserveForm = new FormGroup({
    amount: new FormControl<number | null>(null, [Validators.required, Validators.min(0), money]),
    reason: new FormControl('', { nonNullable: true, validators: [Validators.required, notBlank, Validators.maxLength(500)] }),
  });
  protected readonly approveReason = new FormControl('', { nonNullable: true, validators: [Validators.maxLength(500)] });
  protected readonly rejectReason = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, notBlank, Validators.maxLength(500)],
  });

  ngOnInit(): void {
    this.reload();
  }

  protected has(action: StaffAction): boolean {
    return this.actions().includes(action);
  }

  protected toggle(form: StaffAction): void {
    this.openForm.update((current) => (current === form ? null : form));
    if (form === 'changeReserve') {
      this.reserveForm.reset({ amount: this.claim()?.reserveAmount ?? null, reason: '' });
    }
  }

  protected assign(): void {
    this.run(this.staffApi.assignToMe(this.claimNumber()));
  }

  protected requestInformation(): void {
    if (this.invalid(this.question)) return;
    this.run(this.staffApi.requestInformation(this.claimNumber(), this.question.value.trim()));
  }

  protected changeReserve(): void {
    if (this.invalid(this.reserveForm)) return;
    const { amount, reason } = this.reserveForm.getRawValue();
    this.run(this.staffApi.changeReserve(this.claimNumber(), Number(amount), reason.trim()));
  }

  protected approve(): void {
    if (this.invalid(this.approveReason)) return;
    this.run(this.staffApi.approve(this.claimNumber(), this.approveReason.value.trim()));
  }

  protected reject(): void {
    if (this.invalid(this.rejectReason)) return;
    this.run(this.staffApi.reject(this.claimNumber(), this.rejectReason.value.trim()));
  }

  protected settle(): void {
    this.run(this.staffApi.settle(this.claimNumber()));
  }

  private invalid(control: FormControl | FormGroup): boolean {
    if (control.invalid) {
      control.markAllAsTouched();
      return true;
    }
    return false;
  }

  /** Runs an action, then reloads the claim (also after an error, in case someone else changed it). */
  private run(action: Observable<ClaimDetail>): void {
    this.busy.set(true);
    action.subscribe({
      next: () => {
        this.openForm.set(null);
        this.question.reset();
        this.approveReason.reset();
        this.rejectReason.reset();
        this.reload();
      },
      error: () => this.reload(),
    });
  }

  private reload(): void {
    this.claimApi.getClaim(this.claimNumber()).subscribe({
      next: (claim) => {
        this.claim.set(claim);
        this.busy.set(false);
      },
      error: () => {
        this.failed.set(true);
        this.busy.set(false);
      },
    });
  }
}
