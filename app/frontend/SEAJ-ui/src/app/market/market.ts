import { CurrencyPipe, DatePipe, DecimalPipe, DOCUMENT, NgTemplateOutlet } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import {
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { EMPTY, catchError, exhaustMap, from, map, mergeMap, of } from 'rxjs';
import { pollWhileVisible } from '../dashboard/poll-while-visible';
import { DailyPrice, Instrument, MarketOverview } from '../orders/orders.models';
import { OrdersService } from '../orders/orders.service';
import { MarketDataComponent } from '../orders/market-data';
import { MarketClockComponent } from '../dashboard/market-clock/market-clock';

type Ranking = 'gainers' | 'losers' | 'active' | 'performance' | 'trending';
@Component({
  selector: 'app-market',
  imports: [
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    RouterLink,
    MarketDataComponent,
    NgTemplateOutlet,
    MarketClockComponent,
  ],
  templateUrl: './market.html',
  styleUrl: './market.css',
})
export class MarketComponent {
  private readonly http = inject(HttpClient);
  private readonly destroy = inject(DestroyRef);
  private readonly service = inject(OrdersService);
  private readonly document = inject(DOCUMENT);
  readonly fxSessions = signal<{ date: string; rates: Record<string, number> }[]>([]);
  readonly fxError = signal('');
  readonly fxLoading = signal(false);
  private readonly fxRetry = signal(0);
  readonly fxUrl = '/api/v1/market-data/fx-history';
  readonly selectedFx = signal('EUR/USD');
  readonly fxPeriod = signal<7 | 30 | 90>(30);
  readonly forexPairs = [
    { symbol: 'EUR/USD', currency: 'EUR', inverse: true, name: 'Euro / US dollar' },
    { symbol: 'GBP/USD', currency: 'GBP', inverse: true, name: 'British pound / US dollar' },
    { symbol: 'USD/JPY', currency: 'JPY', inverse: false, name: 'US dollar / Japanese yen' },
    { symbol: 'USD/CHF', currency: 'CHF', inverse: false, name: 'US dollar / Swiss franc' },
    { symbol: 'USD/CAD', currency: 'CAD', inverse: false, name: 'US dollar / Canadian dollar' },
    { symbol: 'AUD/USD', currency: 'AUD', inverse: true, name: 'Australian dollar / US dollar' },
  ];
  readonly forexRows = computed(() =>
    this.forexPairs.map((pair) => {
      const points = this.fxSessions().flatMap((session) => {
        const rate = session.rates[pair.currency];
        return Number.isFinite(rate) && rate > 0
          ? [{ date: session.date, value: pair.inverse ? 1 / rate : rate }]
          : [];
      });
      const latest = points.at(-1),
        previous = points.at(-2);
      return {
        ...pair,
        points,
        latest,
        change: latest && previous ? (latest.value / previous.value - 1) * 100 : null,
      };
    }),
  );
  readonly forexChart = computed(() => {
    const pair = this.forexRows().find((row) => row.symbol === this.selectedFx());
    if (!pair?.latest) return null;
    const cutoff = Date.parse(pair.latest.date) - this.fxPeriod() * 86400000;
    const points = pair.points.filter((p) => Date.parse(p.date) >= cutoff);
    const low = Math.min(...points.map((p) => p.value)),
      high = Math.max(...points.map((p) => p.value));
    const span = high - low;
    return {
      ...pair,
      points,
      low,
      high,
      line: points
        .map(
          (p, i) =>
            `${20 + (i / Math.max(points.length - 1, 1)) * 960},${span ? 20 + ((high - p.value) / span) * 180 : 110}`,
        )
        .join(' '),
    };
  });
  viewForex(symbol: string): void {
    this.selectedFx.set(symbol);
    this.chartMode.set('forex');
    const dialog = this.chartDialog().nativeElement;
    if (!dialog.open) dialog.showModal();
  }
  refreshForex(): void {
    this.fxRetry.update((n) => n + 1);
  }

  readonly instruments = signal<Instrument[]>([]);
  readonly overview = signal<MarketOverview | null>(null);
  readonly latestTrade = computed(
    () =>
      this.overview()
        ?.snapshots.map((snapshot) => snapshot.timestamp)
        .filter((timestamp) => Number.isFinite(Date.parse(timestamp)))
        .sort((a, b) => Date.parse(b) - Date.parse(a))[0] ?? null,
  );
  readonly histories = signal<Record<string, DailyPrice[]>>({});
  readonly assetClass = signal('');
  readonly search = signal('');
  readonly ranking = signal<Ranking>('gainers');
  readonly selected = signal('');
  readonly chartMode = signal<'instrument' | 'forex'>('instrument');
  readonly chartDialog = viewChild.required<ElementRef<HTMLDialogElement>>('chartDialog');
  openChart(symbol: string): void {
    this.chartMode.set('instrument');
    this.selected.set(symbol);
    const dialog = this.chartDialog().nativeElement;
    if (!dialog.open) dialog.showModal();
  }
  onChartBackdropClick(event: MouseEvent): void {
    const dialog = this.chartDialog().nativeElement;
    if (event.target !== dialog) return;
    const bounds = dialog.getBoundingClientRect();
    if (
      event.clientX < bounds.left ||
      event.clientX > bounds.right ||
      event.clientY < bounds.top ||
      event.clientY > bounds.bottom
    ) {
      this.closeChart();
    }
  }

  closeChart(): void {
    this.chartDialog().nativeElement.close();
    this.selected.set('');
  }

  readonly loading = signal(true);
  readonly catalogError = signal('');
  readonly quoteError = signal('');
  readonly historyLoading = signal(false);
  readonly historyFailures = signal(0);
  private readonly historyRetry = signal(0);
  private readonly sessionFormatter = new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'America/New_York',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
  private sessionDate(timestamp: string): string {
    return this.sessionFormatter.format(new Date(timestamp));
  }
  readonly historyRequestKey = computed(() => {
    const data = this.overview();
    if (!data?.configured || !this.instruments().length) return '';
    const known = new Set(this.instruments().map((i) => i.symbol));
    return JSON.stringify([
      data.feed,
      data.snapshots
        .filter((s) => known.has(s.symbol))
        .map((s) => [s.symbol, this.sessionDate(s.dailyBar?.timestamp ?? s.timestamp)])
        .sort((a, b) => a[0].localeCompare(b[0])),
    ]);
  });
  retryHistory(): void {
    this.historyRetry.update((n) => n + 1);
  }

  readonly rankings: { key: Ranking; label: string; description: string }[] = [
    {
      key: 'gainers',
      label: 'Top gainers',
      description: 'Largest positive change versus previous close.',
    },
    {
      key: 'losers',
      label: 'Top losers',
      description: 'Largest negative change versus previous close.',
    },
    {
      key: 'active',
      label: 'Most active',
      description: 'Highest reported session volume, in units.',
    },
    {
      key: 'performance',
      label: 'Top performing',
      description:
        'Best return over the latest 20 completed trading sessions; requires 21 closing prices.',
    },
    {
      key: 'trending',
      label: 'Trending',
      description:
        'Highest session volume relative to the average of the previous 20 completed sessions. This measures trading activity, not social interest.',
    },
  ];
  readonly assetClasses = computed(() =>
    [
      ...new Set([
        ...this.instruments().map((i) => (i.assetClass === 'Cash' ? 'Forex' : i.assetClass)),
        'Forex',
      ]),
    ].sort(),
  );
  readonly scoped = computed(() =>
    this.instruments().filter((i) => !this.assetClass() || i.assetClass === this.assetClass()),
  );
  readonly rows = computed(() => {
    const snapshots = new Map(this.overview()?.snapshots.map((s) => [s.symbol, s]));
    return this.scoped().map((instrument) => {
      const snapshot = snapshots.get(instrument.symbol);
      const history = (this.histories()[instrument.symbol] ?? [])
        .filter(
          (p) =>
            p.priceDate.slice(0, 10) <
            this.sessionDate(
              snapshot?.dailyBar?.timestamp ?? snapshot?.timestamp ?? new Date().toISOString(),
            ),
        )
        .filter((p) => Number.isFinite(p.close) && p.close > 0)
        .sort((a, b) => a.priceDate.localeCompare(b.priceDate));
      const closes = history.slice(-21);
      const volumeHistory = history.slice(-20);
      const average =
        volumeHistory.length === 20 &&
        volumeHistory.every((p) => Number.isFinite(p.volume) && p.volume >= 0)
          ? volumeHistory.reduce((sum, p) => sum + p.volume, 0) / 20
          : 0;
      const volume = snapshot?.dailyBar?.volume;
      return {
        ...instrument,
        snapshot,
        change:
          snapshot?.changePercent != null && Number.isFinite(snapshot.changePercent)
            ? snapshot.changePercent
            : null,
        volume: volume != null && Number.isFinite(volume) && volume >= 0 ? volume : null,
        performance: closes.length === 21 ? (closes[20].close / closes[0].close - 1) * 100 : null,
        relativeVolume:
          average > 0 && volume != null && Number.isFinite(volume) && volume >= 0
            ? volume / average
            : null,
      };
    });
  });
  readonly visibleRows = computed(() => {
    const query = this.search().trim().toLowerCase();
    return this.rows().filter((i) => `${i.symbol} ${i.name}`.toLowerCase().includes(query));
  });
  readonly analytics = computed(() => {
    const rows = this.rows();
    const changes = rows.flatMap((r) => (r.change === null ? [] : [r.change]));
    return {
      total: rows.length,
      quoted: rows.filter((r) => !!r.snapshot).length,
      advancing: changes.filter((c) => c > 0).length,
      declining: changes.filter((c) => c < 0).length,
      unchanged: changes.filter((c) => c === 0).length,
      average: changes.length ? changes.reduce((a, b) => a + b, 0) / changes.length : null,
    };
  });
  readonly leaders = computed(() => {
    const key = this.ranking();
    const value = (row: ReturnType<typeof this.rows>[number]) =>
      key === 'active'
        ? row.volume
        : key === 'performance'
          ? row.performance
          : key === 'trending'
            ? row.relativeVolume
            : row.change;
    return this.rows()
      .filter((row) => {
        const n = value(row);
        return n !== null && (key === 'gainers' ? n > 0 : key === 'losers' ? n < 0 : true);
      })
      .sort(
        (a, b) =>
          (key === 'losers' ? 1 : -1) * (value(a)! - value(b)!) || a.symbol.localeCompare(b.symbol),
      )
      .slice(0, 5)
      .map((row) => ({ ...row, value: value(row)! }));
  });
  readonly description = computed(
    () => this.rankings.find((r) => r.key === this.ranking())!.description,
  );

  constructor() {
    effect((onCleanup) => {
      if (this.assetClass() !== 'Forex') return;
      this.fxRetry();
      this.fxLoading.set(true);
      const subscription = pollWhileVisible(this.document, 3_600_000, { immediate: true })
        .pipe(
          exhaustMap(() =>
            this.http
              .get<{
                base: string;
                sessions: { date: string; rates: Record<string, number> }[];
                message: string;
              }>(this.fxUrl)
              .pipe(
                map((data) => {
                  if (data.base !== 'USD' || !Array.isArray(data.sessions))
                    throw new Error('Invalid forex data');
                  return {
                    ...data,
                    sessions: data.sessions
                      .filter(
                        (s) =>
                          /^\d{4}-\d{2}-\d{2}$/.test(s.date) &&
                          s.rates &&
                          typeof s.rates === 'object',
                      )
                      .sort((a, b) => a.date.localeCompare(b.date)),
                  };
                }),
                catchError(() => {
                  this.fxLoading.set(false);
                  this.fxError.set(
                    'Forex rates could not be refreshed. Any displayed prices retain their original dates.',
                  );
                  return EMPTY;
                }),
              ),
          ),
        )
        .subscribe((data) => {
          this.fxSessions.set(data.sessions);
          this.fxError.set(data.message || '');
          this.fxLoading.set(false);
        });
      onCleanup(() => subscription.unsubscribe());
    });

    effect((onCleanup) => {
      const key = this.historyRequestKey();
      this.historyRetry();
      this.historyFailures.set(0);
      if (!key) {
        this.historyLoading.set(false);
        return;
      }
      const [, entries] = JSON.parse(key) as [string, [string, string][]];
      this.histories.set({});
      this.historyLoading.set(entries.length > 0);
      const subscription = from(entries)
        .pipe(
          mergeMap(
            ([symbol]) =>
              this.service.marketBars(symbol, '1Day').pipe(
                map((chart) => {
                  if (chart.message && !chart.bars.length) throw new Error(chart.message);
                  return {
                    symbol,
                    history: chart.bars.map((bar) => ({
                      ...bar,
                      symbol,
                      priceDate: this.sessionDate(bar.timestamp),
                    })),
                  };
                }),
                catchError(() => {
                  this.historyFailures.update((n) => n + 1);
                  return of({ symbol, history: [] as DailyPrice[] });
                }),
              ),
            4,
          ),
        )
        .subscribe({
          next: ({ symbol, history }) =>
            this.histories.update((current) => ({ ...current, [symbol]: history })),
          complete: () => this.historyLoading.set(false),
        });
      onCleanup(() => subscription.unsubscribe());
    });
    this.loadCatalog();
    pollWhileVisible(inject(DOCUMENT), 30_000, { immediate: true })
      .pipe(
        exhaustMap(() =>
          this.service.marketOverview().pipe(
            catchError(() => {
              this.quoteError.set(
                'Market updates are unavailable. Displayed quotes may be stale; retrying every 30 seconds.',
              );
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((data) => {
        this.overview.set(data);
        this.quoteError.set('');
      });
  }
  loadCatalog(): void {
    this.loading.set(true);
    this.catalogError.set('');
    this.http
      .get<Instrument[]>('/api/v1/instruments')
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (instruments) => {
          this.instruments.set(instruments);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.catalogError.set('The instrument catalogue could not be loaded.');
        },
      });
  }
}
