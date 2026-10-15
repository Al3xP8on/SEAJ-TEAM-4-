import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe, DOCUMENT } from '@angular/common';
import {
  EMPTY,
  Observable,
  catchError,
  exhaustMap,
  fromEvent,
  startWith,
  switchMap,
  timer,
} from 'rxjs';
import { Instrument, MarketBar, MarketOverview, MarketSnapshot } from './orders.models';
import { OrdersService } from './orders.service';

interface HistoryPoint { timestamp: string; value: number; time: number; session: string; }
interface ChartPoint extends HistoryPoint { x: number; y: number; }

@Component({
  selector: 'app-market-data',
  imports: [CurrencyPipe, DatePipe, DecimalPipe],
  templateUrl: './market-data.html',
  styleUrl: './market-data.css',
})
export class MarketDataComponent {
  private readonly service = inject(OrdersService);
  private readonly document = inject(DOCUMENT);
  private readonly dateFormatter = new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'America/New_York',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
  readonly instruments = input.required<Instrument[]>();
  readonly selectedSymbol = input('');
  readonly choose = output<string>();
  readonly overview = signal<MarketOverview | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly chartPeriod = signal<'1D' | '1W' | '1M'>('1D');
  readonly timeframe = computed(() => (this.chartPeriod() === '1D' ? '1Min' : '1Day'));
  readonly bars = signal<MarketBar[]>([]);
  readonly chartLoading = signal(false);
  readonly chartError = signal('');
  readonly chartMessage = signal('');
  readonly inspectIndex = signal<number | null>(null);
  private readonly retry = signal(0);

  readonly selectedInstrument = computed(() =>
    this.instruments().find((instrument) => instrument.symbol === this.selectedSymbol()),
  );
  readonly selectedSnapshot = computed(() =>
    this.overview()?.snapshots.find((snapshot) => snapshot.symbol === this.selectedSymbol()),
  );
  readonly unsupported = computed(
    () => this.overview()?.unsupportedSymbols.includes(this.selectedSymbol()) ?? false,
  );
  private readonly canLoadChart = computed(
    () => !!this.overview()?.configured && !this.unsupported(),
  );
  readonly connectionMessage = computed(() => this.error() || this.overview()?.message || '');
  readonly latestMarketTime = computed(() =>
    this.overview()
      ?.snapshots.map((snapshot) => snapshot.timestamp)
      .sort((a, b) => Date.parse(a) - Date.parse(b))
      .at(-1),
  );
  readonly leaders = computed(() => {
    const snapshots = this.overview()?.snapshots ?? [];
    const latestDay = snapshots
      .map((snapshot) => this.sessionDate(snapshot.timestamp))
      .sort()
      .at(-1);
    return snapshots
      .filter(
        (snapshot) =>
          snapshot.changePercent !== null && this.sessionDate(snapshot.timestamp) === latestDay,
      )
      .map((snapshot) => ({
        snapshot,
        changePercent: snapshot.changePercent ?? 0,
        instrument: this.instruments().find((instrument) => instrument.symbol === snapshot.symbol),
      }))
      .filter((entry) => !!entry.instrument)
      .sort(
        (a, b) =>
          b.changePercent - a.changePercent ||
          a.snapshot.symbol.localeCompare(b.snapshot.symbol),
      )
      .slice(0, 5);
  });
  private readonly historyPoints = computed(() =>
    this.bars().map((bar) => ({
      timestamp: bar.timestamp,
      value: bar.close,
      time: Date.parse(bar.timestamp),
      session: this.sessionDate(bar.timestamp),
    })),
  );
  readonly chart = computed(() => {
    const snapshot = this.selectedSnapshot();
    const prices = this.chartPrices(snapshot);
    if (!prices.length) return null;
    const previousClose = this.chartPreviousClose(snapshot, prices);
    const values = prices.map((point) => point.value);
    if (previousClose !== null) values.push(previousClose);
    const low = Math.min(...values);
    const high = Math.max(...values);
    const padding = (high - low) * 0.12 || Math.max(high * 0.01, 0.01);
    const y = (value: number) => 12 + ((high + padding - value) / (high - low + padding * 2)) * 156;
    const points = this.chartPoints(prices, y);
    const boundaries = this.sessionBoundaries(points);
    return {
      points,
      line: points.map((point) => `${point.x},${point.y}`).join(' '),
      first: prices[0].timestamp,
      last: prices.at(-1)!.timestamp,
      low,
      high,
      boundaries,
      sessions: this.sessionLabels(points),
      previousClose: previousClose === null ? null : { value: previousClose, y: y(previousClose) },
    };
  });
  private chartPrices(snapshot: MarketSnapshot | undefined): HistoryPoint[] {
    let prices = this.historyPoints();
    if (this.timeframe() !== '1Min') return prices.slice(this.chartPeriod() === '1W' ? -5 : -30);
    if (snapshot) {
      const session = this.sessionDate(snapshot.timestamp);
      const time = Date.parse(snapshot.timestamp);
      if (!prices.length || time > prices.at(-1)!.time) {
        prices = [...prices, { timestamp: snapshot.timestamp, value: snapshot.price, time, session }];
      }
    }
    const sessions = [...new Set(prices.map(point => point.session))].slice(-2);
    return prices.filter(point => sessions.includes(point.session));
  }

  private chartPreviousClose(snapshot: MarketSnapshot | undefined, prices: HistoryPoint[]) {
    if (this.timeframe() !== '1Min' || !snapshot || snapshot.previousClose === null) return null;
    if (this.sessionDate(snapshot.timestamp) !== prices.at(-1)!.session) return null;
    return Number.isFinite(snapshot.previousClose) && snapshot.previousClose > 0 ? snapshot.previousClose : null;
  }

  private chartPoints(prices: HistoryPoint[], y: (value: number) => number): ChartPoint[] {
    // Keep elapsed time within each session, compressing overnight/weekend gaps.
    let elapsed = 0;
    const offsets = prices.map((point, index) => {
      if (index) {
        const previous = prices[index - 1];
        elapsed += this.timeframe() === '1Min' && point.session !== previous.session
          ? 15 * 60_000 : point.time - previous.time;
      }
      return elapsed;
    });
    return prices.map((point, index) => ({
      ...point,
      x: elapsed ? 12 + (offsets[index] / elapsed) * 476 : 250,
      y: y(point.value),
    }));
  }

  private sessionBoundaries(points: ChartPoint[]) {
    if (this.timeframe() !== '1Min') return [];
    return points.flatMap((point, index) => {
      if (!index || point.session === points[index - 1].session) return [];
      return [{ x: (points[index - 1].x + point.x) / 2, previous: points[index - 1].session, next: point.session }];
    });
  }

  private sessionLabels(points: ChartPoint[]) {
    if (this.timeframe() !== '1Min') return [];
    const sessions = [...new Set(points.map(point => point.session))];
    return sessions.map(session => {
      const first = points.find(point => point.session === session)!;
      const last = points.filter(point => point.session === session).at(-1)!;
      return { date: session, x: Math.max(45, Math.min(455, (first.x + last.x) / 2)) };
    });
  }

  readonly inspectedPoint = computed(() => {
    const points = this.chart()?.points;
    return points?.[this.inspectIndex() ?? points.length - 1] ?? points?.at(-1);
  });

  constructor() {
    effect((onCleanup) => {
      this.retry();
      const subscription = this.pollWhileVisible(10_000, () =>
        this.service.marketOverview().pipe(
          catchError((error) => {
            this.error.set(
              this.message(error, 'Market updates are unavailable. Retrying shortly.'),
            );
            this.loading.set(false);
            return EMPTY;
          }),
        ),
      ).subscribe((data) => {
        this.overview.set(data);
        this.error.set('');
        this.loading.set(false);
      });
      onCleanup(() => subscription.unsubscribe());
    });
    effect((onCleanup) => {
      const symbol = this.selectedSymbol();
      const timeframe = this.timeframe();
      const enabled = this.canLoadChart();
      this.retry();
      this.inspectIndex.set(null);
      this.bars.set([]);
      this.chartError.set('');
      this.chartMessage.set('');
      this.chartLoading.set(false);
      if (!symbol || !enabled) return;
      this.chartLoading.set(true);
      const subscription = this.pollWhileVisible(timeframe === '1Day' ? 300_000 : 60_000, () =>
        this.service.marketBars(symbol, timeframe).pipe(
          catchError((error) => {
            this.chartError.set(this.message(error, 'This price chart could not be loaded.'));
            this.chartLoading.set(false);
            return EMPTY;
          }),
        ),
      ).subscribe((data) => {
        this.bars.set(
          data.bars
            .filter((bar) => Number.isFinite(bar.close) && bar.close > 0)
            .sort((a, b) => Date.parse(a.timestamp) - Date.parse(b.timestamp)),
        );
        this.chartMessage.set(data.message);
        this.chartLoading.set(false);
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  refresh(): void {
    this.retry.update((value) => value + 1);
  }
  inspectChart(event: PointerEvent): void {
    const points = this.chart()?.points;
    const rect = (event.currentTarget as SVGSVGElement).getBoundingClientRect();
    if (!points?.length || !rect.width) return;
    const x = ((event.clientX - rect.left) / rect.width) * 500;
    let left = 0,
      right = points.length - 1;
    while (left < right) {
      const middle = Math.floor((left + right) / 2);
      if (points[middle].x < x) left = middle + 1;
      else right = middle;
    }
    if (left > 0 && Math.abs(points[left - 1].x - x) < Math.abs(points[left].x - x)) left--;
    this.inspectIndex.set(left);
  }
  inspectChartKey(event: KeyboardEvent): void {
    const points = this.chart()?.points;
    if (!points?.length || !['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    const current = this.inspectIndex() ?? points.length - 1;
    let next: number;
    switch (event.key) {
      case 'Home': next = 0; break;
      case 'End': next = points.length - 1; break;
      default: {
        const direction = event.key === 'ArrowLeft' ? -1 : 1;
        next = Math.max(0, Math.min(points.length - 1, current + direction));
      }
    }
    this.inspectIndex.set(next);
  }
  private pollWhileVisible<T>(interval: number, request: () => Observable<T>): Observable<T> {
    return fromEvent(this.document, 'visibilitychange').pipe(
      startWith(null),
      switchMap(() =>
        this.document.hidden ? EMPTY : timer(0, interval).pipe(exhaustMap(request)),
      ),
    );
  }
  private sessionDate(timestamp: string): string {
    return timestamp ? this.dateFormatter.format(new Date(timestamp)) : '';
  }
  private message(error: { error?: { message?: string } }, fallback: string): string {
    return typeof error.error?.message === 'string' ? error.error.message : fallback;
  }
}
