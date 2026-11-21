import { Component, inject } from '@angular/core';
import { ErrorStore } from '../core/http/error-store';

/** Shows the latest API error (ProblemDetail title, detail and field errors). */
@Component({
  selector: 'app-error-banner',
  template: `
    @if (errors.error(); as error) {
      <div class="error-banner" role="alert">
        <div>
          <strong>{{ error.title }}</strong>
          <div>{{ error.detail }}</div>
          @if (error.fieldErrors.length) {
            <ul>
              @for (fieldError of error.fieldErrors; track $index) {
                <li><code>{{ fieldError.field }}</code>: {{ fieldError.message }}</li>
              }
            </ul>
          }
        </div>
        <button type="button" class="link" (click)="errors.clear()" aria-label="Dismiss">✕</button>
      </div>
    }
  `,
})
export class ErrorBanner {
  protected readonly errors = inject(ErrorStore);
}
