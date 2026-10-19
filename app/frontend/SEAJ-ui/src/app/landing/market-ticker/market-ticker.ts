import { Component, DOCUMENT, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CurrencyPipe } from '@angular/common';
import { catchError, exhaustMap, of } from 'rxjs';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucidePause, lucidePlay } from '@ng-icons/lucide';
import { HlmButton } from '@spartan-ng/helm/button';
import { TrendIconComponent } from '../../dashboard/trend/trend-icon';
import { pollWhileVisible } from '../../dashboard/poll-while-visible';

export interface TickerQuote {
  symbol: string;
  label: string;
  price: number;
  /** Percent, e.g. 0.82 for +0.82% */
  changePercent: number | null;
}

// The backend refreshes these every 10 seconds; the landing page doesn't need them that fresh
const REFRESH_MS = 30_000;

/**
 * Index funds and popular stocks scrolling across the landing page. It pauses on hover, on keyboard
 * focus or with its button, and stays still for people who prefer reduced motion. Hidden when prices
 * aren't available rather than showing an empty strip.
 */
@Component({
  selector: 'app-market-ticker',
  imports: [CurrencyPipe, NgIcon, HlmButton, TrendIconComponent],
  providers: [provideIcons({ lucidePause, lucidePlay })],
  templateUrl: './market-ticker.html',
  styleUrl: './market-ticker.css',
})
export class MarketTickerComponent {
  private readonly http = inject(HttpClient);

  readonly quotes = signal<TickerQuote[]>([]);
  readonly paused = signal(false);

  constructor() {
    pollWhileVisible(inject(DOCUMENT), REFRESH_MS, { immediate: true })
      .pipe(
        exhaustMap(() =>
          this.http.get<TickerQuote[]>('/api/v1/market-data/ticker').pipe(catchError(() => of([]))),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((quotes) => this.quotes.set(quotes));
  }
}
