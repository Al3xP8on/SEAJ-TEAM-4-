import { Component, DOCUMENT, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { CurrencyPipe, PercentPipe } from '@angular/common';
import { forkJoin, map, of, switchMap } from 'rxjs';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { DashboardService } from '../dashboard.service';
import { pollWhileVisible } from '../poll-while-visible';
import { LineChartComponent } from '../line-chart/line-chart';
import { TrendIconComponent } from '../trend/trend-icon';
import {
  PerformanceHolding,
  PerformanceSeries,
  Period,
  buildSeries,
  timeframeFor,
} from './performance-series';

const USD = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

// Same refresh as the backend's bar caches, so polling never outpaces new data
const REFRESH_MS: Record<Period, number> = { '1D': 60_000, '1W': 300_000, '1M': 300_000 };

@Component({
  selector: 'app-performance-chart',
  imports: [CurrencyPipe, PercentPipe, HlmCardImports, LineChartComponent, TrendIconComponent],
  templateUrl: './performance-chart.html',
  styleUrl: './performance-chart.css',
})
export class PerformanceChartComponent {
  private readonly dashboard = inject(DashboardService);
  private readonly document = inject(DOCUMENT);

  readonly holdings = input.required<PerformanceHolding[]>();

  readonly periods: Period[] = ['1D', '1W', '1M'];
  readonly period = signal<Period>('1M');
  /** null while loading */
  readonly series = signal<PerformanceSeries | null>(null);

  /**
   * Only a change of period or of what is held triggers a reload; the dashboard's 2-second
   * refresh hands over a new array each time, which must not refetch every symbol's history.
   */
  private readonly request = computed(
    () => ({
      period: this.period(),
      key: this.holdings()
        .map((h) => `${h.symbol}:${h.quantity}`)
        .sort()
        .join(','),
    }),
    { equal: (a, b) => a.period === b.period && a.key === b.key },
  );

  readonly points = computed(() => this.series()?.points ?? []);

  readonly summary = computed(() => {
    const points = this.points();
    if (points.length < 2) {
      return null;
    }
    const first = points[0].value;
    const last = points.at(-1)!.value;
    return { change: last - first, percent: first > 0 ? (last - first) / first : 0 };
  });

  readonly dateFormat = computed(() => (this.period() === '1D' ? 'HH:mm' : 'MMM d'));

  readonly description = computed(() => {
    const points = this.points();
    return points.length < 2
      ? ''
      : `Holdings value over ${this.period()}, from ${USD.format(points[0].value)} to ${USD.format(points.at(-1)!.value)}`;
  });

  constructor() {
    toObservable(this.request)
      .pipe(
        switchMap(({ period }) => {
          const holdings = this.holdings();
          this.series.set(null);
          if (holdings.length === 0) {
            return of({ points: [], missing: [] });
          }
          const timeframe = timeframeFor(period);
          return pollWhileVisible(this.document, REFRESH_MS[period], { immediate: true }).pipe(
            switchMap(() =>
              forkJoin(holdings.map((h) => this.dashboard.bars(h.symbol, timeframe))),
            ),
            map((charts) => buildSeries(holdings, charts, period)),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((series) => this.series.set(series));
  }

  select(period: Period): void {
    this.period.set(period);
  }
}
