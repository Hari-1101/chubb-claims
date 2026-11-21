import { Component, computed, inject, input, OnInit, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ClaimApi } from '../core/api/claim-api';
import { ClaimDetail } from '../core/models/api.models';
import { ClaimFacts } from '../shared/claim-facts';
import { ClaimTimeline } from '../shared/claim-timeline';
import { InformationRequestList } from '../shared/information-request-list';
import { notBlank } from '../shared/validators';

/** A claimant's view of one claim: facts, timeline, questions, and a form to answer an open question. */
@Component({
  selector: 'app-claimant-claim-detail',
  imports: [ReactiveFormsModule, RouterLink, ClaimFacts, ClaimTimeline, InformationRequestList],
  template: `
    <p><a routerLink="/claims">← My claims</a></p>
    @if (claim(); as claim) {
      <h1>Claim {{ claim.claimNumber }}</h1>

      @if (openRequest(); as request) {
        <section class="card highlight">
          <h2>We need more information</h2>
          <p><strong>{{ request.question }}</strong></p>
          <form class="form" (submit)="$event.preventDefault(); respond(request.id)" novalidate>
            <textarea [formControl]="response" rows="4" maxlength="2000" placeholder="Your answer"></textarea>
            @if (response.invalid && response.touched) {
              <span class="field-error">Please enter your answer (up to 2000 characters).</span>
            }
            <div class="actions">
              <button type="submit" [disabled]="saving()">{{ saving() ? 'Sending…' : 'Send answer' }}</button>
            </div>
          </form>
        </section>
      }

      <div class="grid-2">
        <section class="card">
          <h2>Details</h2>
          <app-claim-facts [claim]="claim" />
        </section>
        <section class="card">
          <h2>Timeline</h2>
          <app-claim-timeline [history]="claim.statusHistory" />
        </section>
      </div>

      <section class="card">
        <h2>Questions from your claims officer</h2>
        <app-information-request-list [requests]="claim.informationRequests" />
      </section>
    } @else if (!failed()) {
      <p class="muted">Loading…</p>
    }
  `,
})
export class ClaimantClaimDetail implements OnInit {
  /** From the route (:claimNumber). */
  readonly claimNumber = input.required<string>();

  private readonly claimApi = inject(ClaimApi);

  protected readonly claim = signal<ClaimDetail | null>(null);
  protected readonly failed = signal(false);
  protected readonly saving = signal(false);
  protected readonly response = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, notBlank, Validators.maxLength(2000)],
  });

  protected readonly openRequest = computed(() => this.claim()?.informationRequests.find((r) => !r.response));

  ngOnInit(): void {
    this.claimApi.getClaim(this.claimNumber()).subscribe({
      next: (claim) => this.claim.set(claim),
      error: () => this.failed.set(true),
    });
  }

  protected respond(requestId: number): void {
    if (this.response.invalid) {
      this.response.markAsTouched();
      return;
    }
    this.saving.set(true);
    this.claimApi.respondToInformationRequest(this.claimNumber(), requestId, this.response.value.trim()).subscribe({
      next: (claim) => {
        this.claim.set(claim);
        this.response.reset();
        this.saving.set(false);
      },
      error: () => this.saving.set(false),
    });
  }
}
