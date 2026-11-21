import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ClaimApi } from '../core/api/claim-api';
import { Policy } from '../core/models/api.models';
import { MoneyPipe } from '../shared/money.pipe';
import { money, notBlank, notInFuture, todayIso } from '../shared/validators';

/** Report a new incident on one of my policies. Validation mirrors the backend's SubmitClaimRequest. */
@Component({
  selector: 'app-new-claim',
  imports: [ReactiveFormsModule, MoneyPipe],
  templateUrl: './new-claim.html',
})
export class NewClaim {
  private readonly claimApi = inject(ClaimApi);
  private readonly router = inject(Router);

  protected readonly policies = signal<Policy[] | null>(null);
  protected readonly saving = signal(false);
  protected readonly today = todayIso();

  protected readonly form = new FormGroup({
    policyNumber: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    incidentDate: new FormControl('', { nonNullable: true, validators: [Validators.required, notInFuture] }),
    description: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, notBlank, Validators.maxLength(2000)],
    }),
    estimatedAmount: new FormControl<number | null>(null, [Validators.required, Validators.min(0.01), money]),
  });

  private readonly selectedPolicyNumber = toSignal(this.form.controls.policyNumber.valueChanges, { initialValue: '' });

  /** The chosen policy, to show its currency and coverage limit next to the amount. */
  protected readonly selectedPolicy = computed(() =>
    this.policies()?.find((p) => p.policyNumber === this.selectedPolicyNumber()),
  );

  constructor() {
    this.claimApi.myPolicies().subscribe((policies) => this.policies.set(policies));
  }

  protected showError(name: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || control.dirty);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.claimApi
      .submitClaim({
        policyNumber: value.policyNumber,
        incidentDate: value.incidentDate,
        description: value.description.trim(),
        estimatedAmount: Number(value.estimatedAmount),
      })
      .subscribe({
        next: (claim) => void this.router.navigate(['/claims', claim.claimNumber]),
        error: () => this.saving.set(false),
      });
  }
}
