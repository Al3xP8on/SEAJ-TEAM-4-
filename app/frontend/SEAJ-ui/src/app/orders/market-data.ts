import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { catchError, from, map, mergeMap, of, switchMap, timer, toArray } from 'rxjs';
import { DailyPrice, Instrument, Quote } from './orders.models';
import { OrdersService } from './orders.service';

@Component({
  selector: 'app-market-data',
  imports: [CurrencyPipe, DatePipe, DecimalPipe],
  templateUrl: './market-data.html',
  styleUrl: './market-data.css',
})
export class MarketDataComponent {
  private readonly service = inject(OrdersService);
  readonly instruments = input.required<Instrument[]>();
  readonly selectedSymbol = input('');
  readonly quote = input<Quote | null>(null);
  readonly choose = output<string>();
  readonly prices = signal<Record<string, DailyPrice[]>>({});
  readonly loading = signal(true);
  readonly failedSymbols = signal<string[]>([]);
  private readonly retry = signal(0);

  readonly selectedInstrument = computed(() =>
    this.instruments().find((instrument) => instrument.symbol === this.selectedSymbol()),
  );
  readonly selectedPrices = computed(() => this.prices()[this.selectedSymbol()] ?? []);
  readonly latest = computed(() => this.selectedPrices().at(-1));
  readonly selectedQuote = computed(() =>
    this.quote()?.symbol === this.selectedSymbol() ? this.quote() : null,
  );
  readonly selectedGain = computed(() => this.dailyGain(this.selectedPrices()));
  readonly latestDate = computed(
    () =>
      Object.values(this.prices())
        .map((prices) => prices.at(-1)?.priceDate ?? '')
        .sort()
        .at(-1) ?? '',
  );
  readonly leaders = computed(() =>
    this.instruments()
      .map((instrument) => {
        const prices = this.prices()[instrument.symbol] ?? [];
        return { instrument, latest: prices.at(-1), gain: this.dailyGain(prices) };
      })
      // Rank a single trading date rather than mix older closes into today's list.
      .filter((entry) => entry.latest?.priceDate === this.latestDate() && entry.gain !== null)
      .sort((a, b) => b.gain! - a.gain! || a.instrument.symbol.localeCompare(b.instrument.symbol))
      .slice(0, 5),
  );
  readonly chart = computed(() => {
    const prices = this.selectedPrices().slice(-30);
    if (!prices.length) return null;
    const low = Math.min(...prices.map((price) => price.close));
    const high = Math.max(...prices.map((price) => price.close));
    const padding = (high - low) * 0.12 || Math.max(high * 0.01, 0.01);
    const min = low - padding;
    const max = high + padding;
    const points = prices.map((price, index) => ({
      price,
      x: prices.length === 1 ? 250 : 12 + (index / (prices.length - 1)) * 476,
      y: 12 + ((max - price.close) / (max - min)) * 156,
    }));
    return {
      points,
      line: points.map((point) => `${point.x},${point.y}`).join(' '),
      first: prices[0].priceDate,
      last: prices.at(-1)!.priceDate,
      low,
      high,
    };
  });

  constructor() {
    effect((onCleanup) => {
      const instruments = this.instruments();
      this.retry();
      const subscription = timer(0, 60_000)
        .pipe(
          switchMap(() => {
            this.loading.set(true);
            return from(instruments).pipe(
              mergeMap(
                (instrument) =>
                  this.service.priceHistory(instrument.symbol).pipe(
                    map((prices) => ({ symbol: instrument.symbol, prices, failed: false })),
                    catchError(() => of({ symbol: instrument.symbol, prices: [], failed: true })),
                  ),
                4,
              ),
              toArray(),
            );
          }),
        )
        .subscribe((results) => {
          this.prices.set(
            Object.fromEntries(
              results.map((result) => [
                result.symbol,
                result.prices
                  .filter((price) => Number.isFinite(price.close) && price.close > 0)
                  .sort((a, b) => a.priceDate.localeCompare(b.priceDate)),
              ]),
            ),
          );
          this.failedSymbols.set(
            results.filter((result) => result.failed).map((result) => result.symbol),
          );
          this.loading.set(false);
        });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  refresh(): void {
    this.retry.update((value) => value + 1);
  }

  private dailyGain(prices: DailyPrice[]): number | null {
    if (prices.length < 2) return null;
    const previous = prices.at(-2)!.close;
    return ((prices.at(-1)!.close - previous) / previous) * 100;
  }
}
