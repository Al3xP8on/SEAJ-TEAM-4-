import { Component, DOCUMENT, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { countdown, marketStatus, nextChange } from '../market-status';
import { pollWhileVisible } from '../poll-while-visible';

const NEW_YORK_CLOCK = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'America/New_York',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
});

const NEW_YORK_DAY = new Intl.DateTimeFormat('en-US', {
  timeZone: 'America/New_York',
  weekday: 'short',
});

/**
 * A slim strip above the dashboard heading: NYSE status with a countdown to the next open or
 * close, the time in New York, and when prices were last updated.
 * Ticks every second while the tab is visible. It isn't a live region, so screen readers
 * read it when they reach it rather than every second.
 */
@Component({
  selector: 'app-market-clock',
  template: `
    <div class="strip" [attr.data-status]="status()">
      <p>
        <span class="dot" aria-hidden="true"></span>
        <span class="market-status">NYSE {{ status() }} · {{ change() }}</span>
      </p>
      <p class="times">
        <time class="clock" [attr.datetime]="now().toISOString()">{{ clock() }} ET</time>
        @if (pricesAsOf(); as asOf) {
          · Prices as of
          <time class="as-of" [attr.datetime]="asOf">{{ asOfClock() }} ET</time>
        }
      </p>
    </div>
  `,
  styleUrl: './market-clock.css',
})
export class MarketClockComponent {
  /** Time of the newest trade in the market data, so holidays show as closed */
  readonly latestTrade = input<string | null>(null);
  /** When the market data was last refreshed */
  readonly pricesAsOf = input<string | null>(null);

  readonly now = signal(new Date());

  readonly status = computed(() => marketStatus(this.now(), this.latestTrade()));

  readonly change = computed(() => {
    const now = this.now();
    const next = nextChange(now, this.status());
    const ms = next.getTime() - now.getTime();
    if (this.status() === 'open') {
      return `closes in ${countdown(ms)}`;
    }
    return ms < 24 * 3_600_000
      ? `opens in ${countdown(ms)}`
      : `opens ${NEW_YORK_DAY.format(next)} 09:30 ET`;
  });

  readonly clock = computed(() => NEW_YORK_CLOCK.format(this.now()));

  /** Also New York time, so both times on the strip are in the same zone */
  readonly asOfClock = computed(() => {
    const asOf = this.pricesAsOf();
    return asOf ? NEW_YORK_CLOCK.format(new Date(asOf)) : '';
  });

  constructor() {
    pollWhileVisible(inject(DOCUMENT), 1000, { immediate: true })
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.now.set(new Date()));
  }
}
