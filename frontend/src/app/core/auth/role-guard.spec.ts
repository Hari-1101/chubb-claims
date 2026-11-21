import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { AuthStore } from './auth-store';
import { findDemoUser } from './demo-users';
import { roleGuard } from './role-guard';

describe('roleGuard', () => {
  let auth: AuthStore;
  let router: Router;

  function run(role: 'CLAIMANT' | 'STAFF'): boolean | UrlTree {
    return TestBed.runInInjectionContext(() =>
      roleGuard(role)({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as boolean | UrlTree;
  }

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({});
    auth = TestBed.inject(AuthStore);
    router = TestBed.inject(Router);
  });

  it('lets a staff user into staff pages', () => {
    auth.login(findDemoUser('S001')!);
    expect(run('STAFF')).toBe(true);
  });

  it('sends a claimant away from staff pages to their own home', () => {
    auth.login(findDemoUser('C001')!);
    const result = run('STAFF');
    expect(router.serializeUrl(result as UrlTree)).toBe('/claims');
  });

  it('sends a staff user away from claimant pages to the work queue', () => {
    auth.login(findDemoUser('S003')!);
    const result = run('CLAIMANT');
    expect(router.serializeUrl(result as UrlTree)).toBe('/staff/queue');
  });

  it('sends someone who is not logged in to the login page', () => {
    const result = run('CLAIMANT');
    expect(router.serializeUrl(result as UrlTree)).toBe('/login');
  });
});
