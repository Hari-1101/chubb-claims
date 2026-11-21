import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { forkJoin, interval } from 'rxjs';
import { StaffApi } from '../core/api/staff-api';
import { DEMO_USERS } from '../core/auth/demo-users';
import { CLAIM_STATUSES, ExposureReport, WorkloadReport } from '../core/models/api.models';
import { MoneyPipe } from '../shared/money.pipe';
import { statusLabel } from '../shared/status-badge';

const AUTO_REFRESH_MS = 10_000;

/** Liability exposure and team workload, read from the reporting module (event-driven read model). */
@Component({
  selector: 'app-dashboard',
  imports: [DatePipe, FormsModule, MoneyPipe],
  templateUrl: './dashboard.html',
})
export class Dashboard {
  private readonly staffApi = inject(StaffApi);
  private readonly destroyRef = inject(DestroyRef);

  /** Team names come from the demo users (Motor, Property). */
  protected readonly teams = [...new Set(DEMO_USERS.filter((u) => u.team).map((u) => u.team!))];
  protected readonly openStatuses = CLAIM_STATUSES.filter((s) => s !== 'SETTLED' && s !== 'REJECTED');
  protected readonly statusLabel = statusLabel;

  protected team = '';
  protected readonly exposure = signal<ExposureReport | null>(null);
  protected readonly workload = signal<WorkloadReport | null>(null);
  protected readonly loading = signal(false);

  constructor() {
    this.refresh(false);
    interval(AUTO_REFRESH_MS)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.refresh(true));
  }

  /** @param silent background refresh: don't pop up the error banner */
  protected refresh(silent: boolean): void {
    this.loading.set(true);
    forkJoin({ exposure: this.staffApi.exposure(silent), workload: this.staffApi.workload(this.team, silent) })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ exposure, workload }) => {
          this.exposure.set(exposure);
          this.workload.set(workload);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  /** The later of the two reports' lastUpdated values. */
  protected lastUpdated(): string | null {
    const times = [this.exposure()?.lastUpdated, this.workload()?.lastUpdated].filter((t): t is string => !!t);
    return times.sort().at(-1) ?? null;
  }
}
