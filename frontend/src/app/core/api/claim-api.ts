import { HttpClient, HttpContext } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { SILENT_ERRORS } from '../http/error-interceptor';
import { ClaimDetail, ClaimNotification, ClaimSummary, Policy, SubmitClaimRequest } from '../models/api.models';

/** Claimant endpoints, plus the claim detail endpoint that staff use too. */
@Injectable({ providedIn: 'root' })
export class ClaimApi {
  private readonly http = inject(HttpClient);

  myPolicies(): Observable<Policy[]> {
    return this.http.get<Policy[]>('/api/me/policies');
  }

  myClaims(): Observable<ClaimSummary[]> {
    return this.http.get<ClaimSummary[]>('/api/claims/mine');
  }

  submitClaim(request: SubmitClaimRequest): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>('/api/claims', request);
  }

  /** Claimants get their own claims only; staff get any claim (including reserve). */
  getClaim(claimNumber: string): Observable<ClaimDetail> {
    return this.http.get<ClaimDetail>(`/api/claims/${encodeURIComponent(claimNumber)}`);
  }

  respondToInformationRequest(claimNumber: string, requestId: number, response: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(
      `/api/claims/${encodeURIComponent(claimNumber)}/information-requests/${requestId}/response`,
      { response },
    );
  }

  /** @param silent true for background polling (no error banner) */
  myNotifications(silent = false): Observable<ClaimNotification[]> {
    return this.http.get<ClaimNotification[]>('/api/me/notifications', {
      context: new HttpContext().set(SILENT_ERRORS, silent),
    });
  }
}
