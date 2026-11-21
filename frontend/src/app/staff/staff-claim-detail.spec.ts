import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthStore } from '../core/auth/auth-store';
import { findDemoUser } from '../core/auth/demo-users';
import { ClaimDetail, ClaimStatus } from '../core/models/api.models';
import { StaffClaimDetail } from './staff-claim-detail';

function claim(status: ClaimStatus, assignedOfficerId: string | null): ClaimDetail {
  return {
    claimNumber: 'CLM-SG-2026-000001',
    policyNumber: 'POL-SG-MOT-001',
    type: 'MOTOR',
    market: 'SG',
    currency: 'SGD',
    incidentDate: '2026-11-01',
    description: 'Scratched door',
    estimatedAmount: 1200,
    reserveAmount: 1200,
    status,
    assignedOfficerId,
    createdAt: '2026-11-02T01:00:00Z',
    updatedAt: '2026-11-02T01:00:00Z',
    statusHistory: [],
    informationRequests: [],
  };
}

describe('StaffClaimDetail action buttons', () => {
  let backend: HttpTestingController;

  /** Renders the page as S001 with the given claim and returns the visible action names. */
  async function renderActionsFor(detail: ClaimDetail): Promise<{ actions: string[]; page: HTMLElement }> {
    const fixture = TestBed.createComponent(StaffClaimDetail);
    fixture.componentRef.setInput('claimNumber', detail.claimNumber);
    fixture.detectChanges();
    backend.expectOne(`/api/claims/${detail.claimNumber}`).flush(detail);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const actions = [...page.querySelectorAll<HTMLButtonElement>('button[data-action]')].map((b) => b.dataset['action']!);
    return { actions, page };
  }

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [StaffClaimDetail],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    TestBed.inject(AuthStore).login(findDemoUser('S001')!);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('shows the decision buttons to the assigned officer of a claim under review', async () => {
    const { actions } = await renderActionsFor(claim('UNDER_REVIEW', 'S001'));
    expect(actions).toEqual(['requestInformation', 'changeReserve', 'approve', 'reject']);
  });

  it('hides all action buttons when another officer is assigned', async () => {
    const { actions, page } = await renderActionsFor(claim('UNDER_REVIEW', 'S002'));
    expect(actions).toEqual([]);
    expect(page.querySelector('[data-testid="no-actions"]')?.textContent).toContain('Only the assigned officer');
  });

  it('hides "Mark as settled" until the claim is approved', async () => {
    const underReview = await renderActionsFor(claim('UNDER_REVIEW', 'S001'));
    expect(underReview.actions).not.toContain('settle');
  });

  it('only offers "Assign to me" on an unassigned submitted claim', async () => {
    const { actions } = await renderActionsFor(claim('SUBMITTED', null));
    expect(actions).toEqual(['assign']);
  });
});
