import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { CurrencyPipe, PercentPipe } from '@angular/common';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { MarketSnapshot } from '../../orders/orders.models';
import { WATCHLIST_LIMIT, WatchlistService } from './watchlist.service';

@Component({
  selector: 'app-watchlist',
  imports: [CurrencyPipe, PercentPipe, HlmCardImports],
  templateUrl: './watchlist.html',
  styleUrl: './watchlist.css',
})
export class WatchlistComponent {
  private readonly watchlist = inject(WatchlistService);

  readonly accountId = input.required<string>();
  readonly snapshots = input.required<MarketSnapshot[]>();
  readonly names = input(new Map<string, string>());

  readonly error = signal('');
  readonly limit = WATCHLIST_LIMIT;

  /** Symbols that can be added: those with market data, falling back to known instruments */
  readonly available = computed(() => {
    const priced = this.snapshots().map((s) => s.symbol);
    return (priced.length > 0 ? priced : [...this.names().keys()]).sort();
  });

  readonly rows = computed(() => {
    const bySymbol = new Map(this.snapshots().map((s) => [s.symbol, s]));
    return this.watchlist.symbols().map((symbol) => {
      const snapshot = bySymbol.get(symbol);
      return {
        symbol,
        name: this.names().get(symbol) ?? null,
        price: snapshot?.price ?? null,
        change: snapshot?.changePercent == null ? null : snapshot.changePercent / 100,
      };
    });
  });

  readonly full = computed(() => this.watchlist.symbols().length >= WATCHLIST_LIMIT);

  constructor() {
    effect(() => this.watchlist.use(this.accountId()));
  }

  add(event: Event, field: HTMLInputElement): void {
    event.preventDefault();
    const symbol = field.value.trim().toUpperCase();
    if (!symbol) {
      this.error.set('Enter a ticker symbol, such as AAPL.');
    } else if (!this.available().includes(symbol)) {
      this.error.set(`${symbol} isn't a tradable instrument.`);
    } else if (this.watchlist.symbols().includes(symbol)) {
      this.error.set(`${symbol} is already on your watchlist.`);
    } else {
      this.watchlist.add(symbol);
      field.value = '';
      this.error.set('');
    }
  }

  remove(symbol: string): void {
    this.watchlist.remove(symbol);
  }
}
