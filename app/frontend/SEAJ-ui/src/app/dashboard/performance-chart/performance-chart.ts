import { Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DatePipe, PercentPipe } from '@angular/common';
import { forkJoin, interval, map, of, startWith, switchMap } from 'rxjs';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { DashboardService } from '../dashboard.service';
import {
  PerformanceHolding,
  PerformanceSeries,
  Period,
  buildSeries,
  timeframeFor,
} from './performance-series';

// Same refresh as the backend's bar caches, so polling never outpaces new data
const REFRESH_MS: Record<Period, number> = { '1D': 60_000, '1W': 300_000, '1M': 300_000 };

// SVG drawing area; the chart scales to the card's width
const WIDTH = 600;
const HEIGHT = 180;
const PADDING = 12;

@Component({
  selector: 'app-performance-chart',
  imports: [CurrencyPipe, DatePipe, PercentPipe, HlmCardImports],
  templateUrl: './performance-chart.html',
  styleUrl: './performance-chart.css',
})
export class PerformanceChartComponent {
  private readonly dashboard = inject(DashboardService);

  readonly holdings = input.required<PerformanceHolding[]>();

  readonly periods: Period[] = ['1D', '1W', '1M'];
  readonly period = signal<Period>('1M');
  /** null while loading */
  readonly series = signal<PerformanceSeries | null>(null);
  readonly inspectIndex = signal<number | null>(null);

  readonly width = WIDTH;
  readonly height = HEIGHT;

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
    const values = points.map((p) => p.value);
    return {
      change: last - first,
      percent: first > 0 ? (last - first) / first : 0,
      high: Math.max(...values),
      low: Math.min(...values),
      from: points[0].time,
      to: points.at(-1)!.time,
    };
  });

  readonly plot = computed(() => {
    const points = this.points();
    if (points.length < 2) {
      return null;
    }
    const values = points.map((p) => p.value);
    const min = Math.min(...values);
    const range = Math.max(...values) - min || 1;
    const coords = points.map((p, i) => ({
      x: (i / (points.length - 1)) * WIDTH,
      y: PADDING + (1 - (p.value - min) / range) * (HEIGHT - 2 * PADDING),
    }));
    const line = coords.map((c) => `${c.x.toFixed(1)},${c.y.toFixed(1)}`).join(' ');
    return {
      coords,
      line,
      area: `M0,${HEIGHT} L${line.replaceAll(' ', ' L')} L${WIDTH},${HEIGHT} Z`,
    };
  });

  readonly inspected = computed(() => {
    const index = this.inspectIndex();
    return index === null ? null : (this.points()[index] ?? null);
  });

  readonly marker = computed(() => {
    const index = this.inspectIndex();
    return index === null ? null : (this.plot()?.coords[index] ?? null);
  });

  readonly dateFormat = computed(() => (this.period() === '1D' ? 'HH:mm' : 'MMM d'));

  constructor() {
    toObservable(this.request)
      .pipe(
        switchMap(({ period }) => {
          const holdings = this.holdings();
          this.series.set(null);
          this.inspectIndex.set(null);
          if (holdings.length === 0) {
            return of({ points: [], missing: [] });
          }
          const timeframe = timeframeFor(period);
          return interval(REFRESH_MS[period]).pipe(
            startWith(0),
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

  /** Arrow keys step through points, Home and End jump to the ends, Escape clears */
  onKey(event: KeyboardEvent): void {
    const last = this.points().length - 1;
    if (last < 0) {
      return;
    }
    const current = this.inspectIndex() ?? last;
    const next: Record<string, number | null> = {
      ArrowLeft: Math.max(0, current - 1),
      ArrowRight: Math.min(last, current + 1),
      Home: 0,
      End: last,
      Escape: null,
    };
    if (event.key in next) {
      event.preventDefault();
      this.inspectIndex.set(next[event.key]);
    }
  }

  onPointer(event: PointerEvent): void {
    const svg = event.currentTarget as SVGElement;
    const rect = svg.getBoundingClientRect();
    const last = this.points().length - 1;
    if (last < 1 || !rect.width) {
      return;
    }
    const ratio = Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width));
    this.inspectIndex.set(Math.round(ratio * last));
  }

  clearInspect(): void {
    this.inspectIndex.set(null);
  }
}
