import { Pipe, PipeTransform } from '@angular/core';

/**
 * Formats an amount in its own currency, e.g. 1500000 + 'IDR' → "IDR 1,500,000.00".
 * Always pass the currency that belongs to the amount: we never convert or mix currencies.
 */
@Pipe({ name: 'money' })
export class MoneyPipe implements PipeTransform {
  transform(amount: number | null | undefined, currency: string): string {
    if (amount === null || amount === undefined) {
      return '—';
    }
    return new Intl.NumberFormat('en', { style: 'currency', currency, currencyDisplay: 'code' }).format(amount);
  }
}
