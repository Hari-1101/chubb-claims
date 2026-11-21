import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthStore, homeFor } from './auth-store';
import { DEMO_USERS, DemoUser } from './demo-users';

/** Demo login: pick one of the seeded users. No password; see demo-users.ts. */
@Component({
  selector: 'app-login',
  template: `
    <h1>Choose a demo user</h1>
    <p class="muted">There is no real login in this demo. Picking a user sets the identity headers the backend uses.</p>

    <div class="grid-2">
      <section class="card">
        <h2>Claimants</h2>
        @for (user of claimants; track user.id) {
          <button type="button" class="user-choice" (click)="pick(user)">
            <strong>{{ user.name }}</strong>
            <span class="muted">{{ user.id }} · policy holder</span>
          </button>
        }
      </section>
      <section class="card">
        <h2>Claims staff</h2>
        @for (user of staff; track user.id) {
          <button type="button" class="user-choice" (click)="pick(user)">
            <strong>{{ user.name }}</strong>
            <span class="muted">{{ user.id }} · {{ user.team }} team</span>
          </button>
        }
      </section>
    </div>
  `,
})
export class Login {
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);

  protected readonly claimants = DEMO_USERS.filter((u) => u.role === 'CLAIMANT');
  protected readonly staff = DEMO_USERS.filter((u) => u.role === 'STAFF');

  protected pick(user: DemoUser): void {
    this.auth.login(user);
    void this.router.navigateByUrl(homeFor(user.role));
  }
}
