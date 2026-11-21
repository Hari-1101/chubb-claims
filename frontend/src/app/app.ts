import { Component, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationStart, Router, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';
import { Header } from './core/layout/header';
import { ErrorStore } from './core/http/error-store';
import { ErrorBanner } from './shared/error-banner';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Header, ErrorBanner],
  template: `
    <app-header />
    <main class="container">
      <app-error-banner />
      <router-outlet />
    </main>
  `,
})
export class App {
  constructor() {
    // An error belongs to the page it happened on: clear it when navigating away.
    const errors = inject(ErrorStore);
    inject(Router)
      .events.pipe(filter((e) => e instanceof NavigationStart), takeUntilDestroyed())
      .subscribe(() => errors.clear());
  }
}
