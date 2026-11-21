import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { authInterceptor } from './auth-interceptor';
import { AuthStore } from './auth-store';
import { findDemoUser } from './demo-users';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let auth: AuthStore;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthStore);
  });

  afterEach(() => backend.verify());

  it('adds X-User-Id and X-User-Role to /api calls for the selected user', () => {
    auth.login(findDemoUser('S002')!);

    http.get('/api/staff/claims').subscribe();

    const req = backend.expectOne('/api/staff/claims');
    expect(req.request.headers.get('X-User-Id')).toBe('S002');
    expect(req.request.headers.get('X-User-Role')).toBe('STAFF');
    req.flush({});
  });

  it('adds no headers when nobody is logged in', () => {
    http.get('/api/me/policies').subscribe();

    const req = backend.expectOne('/api/me/policies');
    expect(req.request.headers.has('X-User-Id')).toBe(false);
    expect(req.request.headers.has('X-User-Role')).toBe(false);
    req.flush([]);
  });

  it('does not send the identity headers to non-API URLs', () => {
    auth.login(findDemoUser('C001')!);

    http.get('/assets/config.json').subscribe();

    const req = backend.expectOne('/assets/config.json');
    expect(req.request.headers.has('X-User-Id')).toBe(false);
    req.flush({});
  });
});
