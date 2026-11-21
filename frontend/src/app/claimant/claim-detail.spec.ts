import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthStore } from '../core/auth/auth-store';
import { findDemoUser } from '../core/auth/demo-users';
import { ClaimDetail } from '../core/models/api.models';
import { ClaimantClaimDetail } from './claim-detail';

const claimNumber = 'CLM-SG-2026-000001';

function claimWithQuestion(answer: string | null): ClaimDetail {
  return {
    claimNumber,
    policyNumber: 'POL-SG-MOT-001',
    type: 'MOTOR',
    market: 'SG',
    currency: 'SGD',
    incidentDate: '2026-11-01',
    description: 'Scratched door',
    estimatedAmount: 1200,
    status: answer ? 'UNDER_REVIEW' : 'INFO_REQUESTED',
    assignedOfficerId: 'S001',
    createdAt: '2026-11-02T01:00:00Z',
    updatedAt: '2026-11-02T01:00:00Z',
    statusHistory: [],
    informationRequests: [
      {
        id: 7,
        question: 'Please provide the police report number',
        response: answer,
        requestedBy: 'S001',
        requestedAt: '2026-11-03T01:00:00Z',
        respondedAt: answer ? '2026-11-04T01:00:00Z' : null,
      },
    ],
  };
}

describe('ClaimantClaimDetail answer form', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [ClaimantClaimDetail],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    TestBed.inject(AuthStore).login(findDemoUser('C001')!);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('submitting the answer calls the API and shows the updated claim', async () => {
    const fixture = TestBed.createComponent(ClaimantClaimDetail);
    fixture.componentRef.setInput('claimNumber', claimNumber);
    fixture.detectChanges();
    backend.expectOne(`/api/claims/${claimNumber}`).flush(claimWithQuestion(null));
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;

    const answer = page.querySelector<HTMLTextAreaElement>('form textarea')!;
    answer.value = 'PR-12345';
    answer.dispatchEvent(new Event('input'));
    page.querySelector<HTMLButtonElement>('form button[type="submit"]')!.click();

    const post = backend.expectOne(`/api/claims/${claimNumber}/information-requests/7/response`);
    expect(post.request.body).toEqual({ response: 'PR-12345' });
    post.flush(claimWithQuestion('PR-12345'));
    await fixture.whenStable();

    expect(page.textContent).toContain('PR-12345');
    expect(page.querySelector('form')).toBeNull(); // no open question any more
  });
});
