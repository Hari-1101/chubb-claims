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

describe('StaffClaimDetail action forms', () => {
  const claimNumber = 'CLM-SG-2026-000001';
  let backend: HttpTestingController;

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

  async function openPage(detail: ClaimDetail) {
    const fixture = TestBed.createComponent(StaffClaimDetail);
    fixture.componentRef.setInput('claimNumber', claimNumber);
    fixture.detectChanges();
    backend.expectOne(`/api/claims/${claimNumber}`).flush(detail);
    await fixture.whenStable();
    return fixture;
  }

  /** Types into a field the way a user would (sets the value and fires the input event). */
  function type(field: HTMLInputElement | HTMLTextAreaElement, text: string): void {
    field.value = text;
    field.dispatchEvent(new Event('input'));
  }

  it('request information: submitting the form calls the API with the question and reloads the claim', async () => {
    const fixture = await openPage(claim('UNDER_REVIEW', 'S001'));
    const page = fixture.nativeElement as HTMLElement;

    page.querySelector<HTMLButtonElement>('button[data-action="requestInformation"]')!.click();
    await fixture.whenStable();
    type(page.querySelector('form textarea')!, 'Please provide the police report number');
    page.querySelector<HTMLButtonElement>('form button[type="submit"]')!.click();

    const post = backend.expectOne(`/api/staff/claims/${claimNumber}/information-requests`);
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ question: 'Please provide the police report number' });
    post.flush(claim('INFO_REQUESTED', 'S001'));

    // After the action the claim is reloaded and the page shows the new state.
    const reloaded: ClaimDetail = {
      ...claim('INFO_REQUESTED', 'S001'),
      informationRequests: [
        {
          id: 7,
          question: 'Please provide the police report number',
          response: null,
          requestedBy: 'S001',
          requestedAt: '2026-11-21T10:00:00Z',
          respondedAt: null,
        },
      ],
    };
    backend.expectOne(`/api/claims/${claimNumber}`).flush(reloaded);
    await fixture.whenStable();

    expect(page.textContent).toContain('Info requested');
    expect(page.textContent).toContain('Please provide the police report number');
    expect(page.querySelector('button[data-action="approve"]')).toBeNull();
    expect(page.querySelector('button[data-action="reject"]')).toBeNull();
  });

  it('reject: submitting the form calls the API with the reason', async () => {
    const fixture = await openPage(claim('UNDER_REVIEW', 'S001'));
    const page = fixture.nativeElement as HTMLElement;

    page.querySelector<HTMLButtonElement>('button[data-action="reject"]')!.click();
    await fixture.whenStable();
    type(page.querySelector('form input')!, 'Not covered by the policy');
    page.querySelector<HTMLButtonElement>('form button[type="submit"]')!.click();

    const post = backend.expectOne(`/api/staff/claims/${claimNumber}/reject`);
    expect(post.request.body).toEqual({ reason: 'Not covered by the policy' });
    post.flush(claim('REJECTED', 'S001'));
    backend.expectOne(`/api/claims/${claimNumber}`).flush(claim('REJECTED', 'S001'));
  });

  it('approve: submitting the form calls the API with the reason', async () => {
    const fixture = await openPage(claim('UNDER_REVIEW', 'S001'));
    const page = fixture.nativeElement as HTMLElement;

    page.querySelector<HTMLButtonElement>('button[data-action="approve"]')!.click();
    await fixture.whenStable();
    type(page.querySelector('form input')!, 'Covered');
    page.querySelector<HTMLButtonElement>('form button[type="submit"]')!.click();

    const post = backend.expectOne(`/api/staff/claims/${claimNumber}/approve`);
    expect(post.request.body).toEqual({ reason: 'Covered' });
    post.flush(claim('APPROVED', 'S001'));
    backend.expectOne(`/api/claims/${claimNumber}`).flush(claim('APPROVED', 'S001'));
  });

  it('change reserve: submitting the form calls the API with amount and reason', async () => {
    const fixture = await openPage(claim('UNDER_REVIEW', 'S001'));
    const page = fixture.nativeElement as HTMLElement;

    page.querySelector<HTMLButtonElement>('button[data-action="changeReserve"]')!.click();
    await fixture.whenStable();
    type(page.querySelector('form input[type="number"]')!, '7000');
    type(page.querySelector('form input[type="text"]')!, 'Workshop quote');
    page.querySelector<HTMLButtonElement>('form button[type="submit"]')!.click();

    const put = backend.expectOne(`/api/staff/claims/${claimNumber}/reserve`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ amount: 7000, reason: 'Workshop quote' });
    put.flush(claim('UNDER_REVIEW', 'S001'));
    backend.expectOne(`/api/claims/${claimNumber}`).flush(claim('UNDER_REVIEW', 'S001'));
  });
});
