import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { SILENT_ERRORS } from '../http/error-interceptor';
import { ClaimDetail, ClaimSummary, ExposureReport, Page, QueueFilters, WorkloadReport } from '../models/api.models';

/** Staff endpoints: work queue, claim actions and reports. */
@Injectable({ providedIn: 'root' })
export class StaffApi {
  private readonly http = inject(HttpClient);

  queue(filters: QueueFilters, page: number, size: number): Observable<Page<ClaimSummary>> {
    let params = new HttpParams().set('assigned', filters.assigned).set('page', page).set('size', size);
    // Empty status = backend default (open claims only).
    if (filters.status) params = params.set('status', filters.status);
    if (filters.market) params = params.set('market', filters.market);
    if (filters.type) params = params.set('type', filters.type);
    return this.http.get<Page<ClaimSummary>>('/api/staff/claims', { params });
  }

  assignToMe(claimNumber: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${this.claimUrl(claimNumber)}/assign`, null);
  }

  requestInformation(claimNumber: string, question: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${this.claimUrl(claimNumber)}/information-requests`, { question });
  }

  changeReserve(claimNumber: string, amount: number, reason: string): Observable<ClaimDetail> {
    return this.http.put<ClaimDetail>(`${this.claimUrl(claimNumber)}/reserve`, { amount, reason });
  }

  approve(claimNumber: string, reason: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${this.claimUrl(claimNumber)}/approve`, { reason: reason || null });
  }

  reject(claimNumber: string, reason: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${this.claimUrl(claimNumber)}/reject`, { reason });
  }

  settle(claimNumber: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${this.claimUrl(claimNumber)}/settle`, null);
  }

  /** @param silent true for background auto-refresh (no error banner) */
  exposure(silent = false): Observable<ExposureReport> {
    return this.http.get<ExposureReport>('/api/staff/reports/exposure', { context: silentContext(silent) });
  }

  workload(team: string, silent = false): Observable<WorkloadReport> {
    const params = team ? new HttpParams().set('team', team) : undefined;
    return this.http.get<WorkloadReport>('/api/staff/reports/workload', { params, context: silentContext(silent) });
  }

  private claimUrl(claimNumber: string): string {
    return `/api/staff/claims/${encodeURIComponent(claimNumber)}`;
  }
}

function silentContext(silent: boolean): HttpContext {
  return new HttpContext().set(SILENT_ERRORS, silent);
}
