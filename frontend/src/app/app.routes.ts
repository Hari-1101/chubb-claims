import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { AuthStore, homeFor } from './core/auth/auth-store';
import { roleGuard } from './core/auth/role-guard';

export const routes: Routes = [
  { path: 'login', title: 'Choose a demo user', loadComponent: () => import('./core/auth/login').then((m) => m.Login) },

  // Claimant screens
  {
    path: 'claims',
    canActivate: [roleGuard('CLAIMANT')],
    children: [
      { path: '', title: 'My claims', loadComponent: () => import('./claimant/my-claims').then((m) => m.MyClaims) },
      { path: 'new', title: 'New claim', loadComponent: () => import('./claimant/new-claim').then((m) => m.NewClaim) },
      {
        path: ':claimNumber',
        title: 'Claim',
        loadComponent: () => import('./claimant/claim-detail').then((m) => m.ClaimantClaimDetail),
      },
    ],
  },
  {
    path: 'notifications',
    title: 'Notifications',
    canActivate: [roleGuard('CLAIMANT')],
    loadComponent: () => import('./claimant/notifications').then((m) => m.Notifications),
  },

  // Staff screens
  {
    path: 'staff',
    canActivate: [roleGuard('STAFF')],
    children: [
      { path: 'queue', title: 'Work queue', loadComponent: () => import('./staff/work-queue').then((m) => m.WorkQueue) },
      {
        path: 'claims/:claimNumber',
        title: 'Claim',
        loadComponent: () => import('./staff/staff-claim-detail').then((m) => m.StaffClaimDetail),
      },
      { path: 'dashboard', title: 'Dashboard', loadComponent: () => import('./staff/dashboard').then((m) => m.Dashboard) },
      { path: '', pathMatch: 'full', redirectTo: 'queue' },
    ],
  },

  // Start page: the user's home, or the login page.
  {
    path: '',
    pathMatch: 'full',
    redirectTo: () => {
      const user = inject(AuthStore).user();
      return user ? homeFor(user.role) : '/login';
    },
  },
  { path: '**', redirectTo: '' },
];
