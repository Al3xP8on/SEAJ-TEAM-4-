import { Component, DOCUMENT, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DecimalPipe, PercentPipe } from '@angular/common';
import { map, switchMap, tap } from 'rxjs';
import { MarketBar, MarketSnapshot } from '../../orders/orders.models';
import { DashboardService } from '../dashboard.service';
import { pollWhileVisible } from '../poll-while-visible';
import { LineChartComponent, LinePoint } from '../line-chart/line-chart';

// Daily bars are cached for 5 minutes by the backend
const REFRESH_MS = 300_000;

const USD = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

/** The account's holding in this symbol, valued as on the dashboard */
export interface StockPosition {
  quantity: number;
  averageCost: number;
  value: number;
  pnl: number | null;
}

/**
 * One symbol at a glance, shown in the dashboard's pop-up: key prices, the holder's position and
 * the last 30 trading days of closing prices.
 */
@Component({
  selector: 'app-stock-summary',
  imports: [CurrencyPipe, DecimalPipe, PercentPipe, LineChartComponent],
  templateUrl: './stock-summary.html',
  styleUrl: './stock-summary.css',
})
export class StockSummaryComponent {
  private readonly dashboard = inject(DashboardService);

  readonly symbol = input.required<string>();
  readonly snapshot = input<MarketSnapshot | null>(null);
  readonly position = input<StockPosition | null>(null);

  /** null while loading */
  readonly bars = signal<MarketBar[] | null>(null);

  readonly points = computed<LinePoint[]>(() =>
    (this.bars() ?? []).map((bar) => ({ time: bar.timestamp, value: bar.close })),
  );

  /** Latest price, falling back to the last close when there's no live quote */
  readonly price = computed(() => this.snapshot()?.price ?? this.bars()?.at(-1)?.close ?? null);

  readonly today = computed(() => {
    const snapshot = this.snapshot();
    if (!snapshot || snapshot.previousClose === null) {
      return null;
    }
    return {
      amount: snapshot.price - snapshot.previousClose,
      percent: (snapshot.changePercent ?? 0) / 100,
    };
  });

  readonly monthRange = computed(() => {
    const bars = this.bars();
    return bars?.length
      ? { low: Math.min(...bars.map((b) => b.low)), high: Math.max(...bars.map((b) => b.high)) }
      : null;
  });

  readonly falling = computed(() => {
    const points = this.points();
    return points.length > 1 && points.at(-1)!.value < points[0].value;
  });

  readonly description = computed(() => {
    const points = this.points();
    return points.length < 2
      ? ''
      : `${this.symbol()} closing price over the last ${points.length} trading days, from ` +
          `${USD.format(points[0].value)} to ${USD.format(points.at(-1)!.value)}`;
  });

  constructor() {
    const document = inject(DOCUMENT);
    toObservable(this.symbol)
      .pipe(
        tap(() => this.bars.set(null)),
        switchMap((symbol) =>
          pollWhileVisible(document, REFRESH_MS, { immediate: true }).pipe(
            switchMap(() => this.dashboard.bars(symbol, '1Day')),
            map((chart) =>
              [...(chart?.bars ?? [])].sort((a, b) => a.timestamp.localeCompare(b.timestamp)),
            ),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((bars) => this.bars.set(bars));
  }
}
