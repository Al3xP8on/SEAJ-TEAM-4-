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
 * NYSE status with a countdown to the next open or close, and the time in New York.
 * Ticks every second while the tab is visible. It isn't a live region, so screen readers
 * read it when they reach it rather than every second.
 */
@Component({
  selector: 'app-market-clock',
  template: `
    <span class="market-status" [attr.data-status]="status()">
      <span class="dot" aria-hidden="true"></span>NYSE {{ status() }} · {{ change() }} ·
      <time class="clock" [attr.datetime]="now().toISOString()">{{ clock() }} ET</time>
    </span>
  `,
  styleUrl: './market-clock.css',
})
export class MarketClockComponent {
  /** Time of the newest trade in the market data, so holidays show as closed */
  readonly latestTrade = input<string | null>(null);

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

  constructor() {
    pollWhileVisible(inject(DOCUMENT), 1000, { immediate: true })
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.now.set(new Date()));
  }
}
