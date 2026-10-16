import { Component, computed, input } from '@angular/core';
import { CurrencyPipe, PercentPipe } from '@angular/common';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { MarketSnapshot } from '../../orders/orders.models';

const NEW_YORK_DATE = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/New_York' });

export interface Mover {
  symbol: string;
  price: number;
  /** Fraction, e.g. 0.034 for +3.4% */
  change: number;
}

/**
 * The biggest rises and falls since the previous close. Like the Orders page's market cards,
 * only instruments priced in the latest New York session count, so a stale quote can't top the list.
 */
export function marketMovers(
  snapshots: MarketSnapshot[],
  count = 3,
): { gainers: Mover[]; losers: Mover[] } {
  const sessionOf = (timestamp: string) => NEW_YORK_DATE.format(new Date(timestamp));
  const latest = snapshots.map((s) => s.timestamp).reduce((a, b) => (a > b ? a : b), '');
  if (!latest) {
    return { gainers: [], losers: [] };
  }
  const movers = snapshots
    .filter((s) => s.changePercent !== null && sessionOf(s.timestamp) === sessionOf(latest))
    .map((s) => ({ symbol: s.symbol, price: s.price, change: s.changePercent! / 100 }));
  return {
    gainers: movers
      .filter((m) => m.change > 0)
      .sort((a, b) => b.change - a.change)
      .slice(0, count),
    losers: movers
      .filter((m) => m.change < 0)
      .sort((a, b) => a.change - b.change)
      .slice(0, count),
  };
}

@Component({
  selector: 'app-market-movers',
  imports: [CurrencyPipe, PercentPipe, HlmCardImports],
  templateUrl: './market-movers.html',
  styleUrl: './market-movers.css',
})
export class MarketMoversComponent {
  readonly snapshots = input.required<MarketSnapshot[]>();
  readonly names = input(new Map<string, string>());

  readonly groups = computed(() => {
    const { gainers, losers } = marketMovers(this.snapshots());
    return [
      { id: 'gainers', title: 'Top gainers', items: gainers, empty: 'Nothing is up today.' },
      { id: 'losers', title: 'Top losers', items: losers, empty: 'Nothing is down today.' },
    ];
  });
}
