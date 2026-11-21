import { Injectable, signal } from '@angular/core';

export interface DisplayError {
  title: string;
  detail: string;
  fieldErrors: { field: string; message: string }[];
}

/** The error currently shown in the error banner (one at a time). */
@Injectable({ providedIn: 'root' })
export class ErrorStore {
  private readonly current = signal<DisplayError | null>(null);

  readonly error = this.current.asReadonly();

  show(error: DisplayError): void {
    this.current.set(error);
  }

  clear(): void {
    this.current.set(null);
  }
}
