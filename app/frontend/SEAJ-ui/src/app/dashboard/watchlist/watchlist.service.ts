import { Injectable, signal } from '@angular/core';

const KEY_PREFIX = 'seaj.watchlist.';
export const WATCHLIST_LIMIT = 10;

/**
 * The symbols an account follows. Saved in this browser for now, per account so people sharing
 * a browser keep separate lists; moving to a backend table only changes this service.
 * Storage can be unavailable (private windows, blocked site data), so every access is guarded
 * and the list still works for the visit.
 */
@Injectable({ providedIn: 'root' })
export class WatchlistService {
  private readonly list = signal<string[]>([]);
  private accountId: string | null = null;

  readonly symbols = this.list.asReadonly();

  /** Loads the saved list for an account; call when the account is known */
  use(accountId: string): void {
    if (accountId === this.accountId) {
      return;
    }
    this.accountId = accountId;
    try {
      const saved = JSON.parse(localStorage.getItem(KEY_PREFIX + accountId) ?? '[]');
      this.list.set(
        Array.isArray(saved) ? saved.filter((s): s is string => typeof s === 'string') : [],
      );
    } catch {
      this.list.set([]);
    }
  }

  add(symbol: string): void {
    const clean = symbol.trim().toUpperCase();
    if (!clean || this.list().includes(clean) || this.list().length >= WATCHLIST_LIMIT) {
      return;
    }
    this.save([...this.list(), clean]);
  }

  remove(symbol: string): void {
    this.save(this.list().filter((s) => s !== symbol));
  }

  private save(symbols: string[]): void {
    this.list.set(symbols);
    if (!this.accountId) {
      return;
    }
    try {
      localStorage.setItem(KEY_PREFIX + this.accountId, JSON.stringify(symbols));
    } catch {
      // Not saved, but the list still works until the page is closed
    }
  }
}
