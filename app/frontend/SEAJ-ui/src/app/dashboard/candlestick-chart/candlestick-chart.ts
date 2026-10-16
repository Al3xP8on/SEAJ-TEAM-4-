import {
  Component,
  DOCUMENT,
  ElementRef,
  afterNextRender,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
  Injector,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DatePipe, PercentPipe } from '@angular/common';
import { map, switchMap, tap } from 'rxjs';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { MarketBar } from '../../orders/orders.models';
import { DashboardService } from '../dashboard.service';
import { pollWhileVisible } from '../poll-while-visible';

// Daily bars are cached for 5 minutes by the backend; today's candle can still move
const REFRESH_MS = 300_000;

const WIDTH = 600;
const HEIGHT = 220;
const PADDING = 10;

export interface Candle extends MarketBar {
  up: boolean;
  /** Fractional change from open to close */
  change: number;
}

@Component({
  selector: 'app-candlestick-chart',
  imports: [CurrencyPipe, DatePipe, PercentPipe, HlmCardImports],
  templateUrl: './candlestick-chart.html',
  styleUrl: './candlestick-chart.css',
})
export class CandlestickChartComponent {
  private readonly dashboard = inject(DashboardService);
  private readonly injector = inject(Injector);

  readonly symbol = input.required<string>();
  readonly name = input<string | null>(null);
  readonly closed = output<void>();

  private readonly heading = viewChild.required<ElementRef<HTMLHeadingElement>>('heading');

  /** null while loading */
  readonly candles = signal<Candle[] | null>(null);
  readonly inspectIndex = signal<number | null>(null);

  readonly width = WIDTH;
  readonly height = HEIGHT;

  readonly summary = computed(() => {
    const candles = this.candles();
    if (!candles?.length) {
      return null;
    }
    return {
      from: candles[0],
      to: candles.at(-1)!,
      high: Math.max(...candles.map((c) => c.high)),
      low: Math.min(...candles.map((c) => c.low)),
      change: (candles.at(-1)!.close - candles[0].open) / candles[0].open,
    };
  });

  readonly plot = computed(() => {
    const candles = this.candles();
    const s = this.summary();
    if (!candles?.length || !s) {
      return null;
    }
    const range = s.high - s.low || 1;
    const y = (price: number) => PADDING + (1 - (price - s.low) / range) * (HEIGHT - 2 * PADDING);
    const slot = WIDTH / candles.length;
    const body = Math.max(2, slot * 0.6);
    return candles.map((c, i) => {
      const x = slot * i + slot / 2;
      const top = y(Math.max(c.open, c.close));
      return {
        x,
        bodyX: x - body / 2,
        bodyWidth: body,
        bodyY: top,
        bodyHeight: Math.max(1, y(Math.min(c.open, c.close)) - top),
        wickTop: y(c.high),
        wickBottom: y(c.low),
        up: c.up,
      };
    });
  });

  readonly inspected = computed(() => {
    const index = this.inspectIndex();
    return index === null ? null : (this.candles()?.[index] ?? null);
  });

  constructor() {
    const document = inject(DOCUMENT);
    toObservable(this.symbol)
      .pipe(
        tap(() => {
          this.candles.set(null);
          this.inspectIndex.set(null);
          // Take keyboard and screen-reader focus to the chart that just opened
          afterNextRender(() => this.heading().nativeElement.focus(), { injector: this.injector });
        }),
        switchMap((symbol) =>
          pollWhileVisible(document, REFRESH_MS, { immediate: true }).pipe(
            switchMap(() => this.dashboard.bars(symbol, '1Day')),
            map((chart) => toCandles(chart?.bars ?? [])),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((candles) => this.candles.set(candles));
  }

  /** Arrow keys step through days, Home and End jump to the ends, Escape clears */
  onKey(event: KeyboardEvent): void {
    const last = (this.candles()?.length ?? 0) - 1;
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
    const rect = (event.currentTarget as SVGElement).getBoundingClientRect();
    const count = this.candles()?.length ?? 0;
    if (!count || !rect.width) {
      return;
    }
    const index = Math.floor(((event.clientX - rect.left) / rect.width) * count);
    this.inspectIndex.set(Math.min(count - 1, Math.max(0, index)));
  }

  clearInspect(): void {
    this.inspectIndex.set(null);
  }
}

/** Oldest first, with direction worked out once */
export function toCandles(bars: MarketBar[]): Candle[] {
  return [...bars]
    .sort((a, b) => a.timestamp.localeCompare(b.timestamp))
    .map((bar) => ({
      ...bar,
      up: bar.close >= bar.open,
      change: bar.open ? (bar.close - bar.open) / bar.open : 0,
    }));
}
