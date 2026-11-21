import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Matches the backend's @NotBlank: whitespace-only text is not allowed. */
export const notBlank: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' && control.value.trim().length === 0 && control.value.length > 0
    ? { notBlank: true }
    : null;

/** Matches the backend's @Digits(integer = 13, fraction = 2). */
export const money: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = control.value;
  if (value === null || value === undefined || value === '') {
    return null;
  }
  return /^\d{1,13}(\.\d{1,2})?$/.test(String(value)) ? null : { money: true };
};

/** Today's date in the browser as 'YYYY-MM-DD'. */
export function todayIso(): string {
  return new Date().toLocaleDateString('en-CA');
}

/** Incident date can't be in the future (the backend re-checks this in the policy's market time zone). */
export const notInFuture: ValidatorFn = (control: AbstractControl): ValidationErrors | null =>
  control.value && control.value > todayIso() ? { future: true } : null;
