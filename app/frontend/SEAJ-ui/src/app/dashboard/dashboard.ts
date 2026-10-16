import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, exhaustMap, interval, startWith, timer } from 'rxjs';
import { CurrencyPipe, DatePipe, DecimalPipe, PercentPipe } from '@angular/common';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { Router } from '@angular/router';
import { Account, Position } from './dashboard.models';
import { MarketOverview, MarketSnapshot, Order } from '../orders/orders.models';
import { DashboardData, DashboardService } from './dashboard.service';
import { AllocationChartComponent, AllocationSlice } from './allocation-chart/allocation-chart';
import { PortfolioCheckComponent } from './portfolio-check/portfolio-check';
import { RecentOrdersComponent } from './recent-orders/recent-orders';

// The backend refreshes market prices every 10 seconds, so polling faster gains nothing
const MARKET_REFRESH_MS = 10_000;

// Orders that can still fill; the backend holds back cash for open BUYs until then
const OPEN_STATUSES: Order['status'][] = ['NEW', 'PENDING'];

// Holdings the user still owns; closed and liquidated positions are left out
const HELD_STATUSES: Position['status'][] = ['OPEN', 'PARTIALLY_CLOSED'];

@Component({
  selector: 'app-dashboard',
  imports: [
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    PercentPipe,
    HlmCardImports,
    HlmTableImports,
    AllocationChartComponent,
    PortfolioCheckComponent,
    RecentOrdersComponent,
  ],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class DashboardComponent implements OnInit {
  private readonly dashboard = inject(DashboardService);
  private readonly router = inject(Router);

  private readonly destroy = inject(DestroyRef);

  readonly account = signal<Account | null>(null);
  readonly positions = signal<Position[]>([]);
  readonly orders = signal<Order[]>([]);
  readonly market = signal<MarketOverview | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');

  private readonly snapshots = computed(
    () =>
      new Map<string, MarketSnapshot>((this.market()?.snapshots ?? []).map((m) => [m.symbol, m])),
  );

  /** Valued at the latest price where there is one, otherwise at cost */
  readonly holdings = computed(() =>
    this.positions()
      .filter((p) => HELD_STATUSES.includes(p.status) && p.quantity > 0)
      .map((p) => {
        const snapshot = this.snapshots().get(p.symbol);
        const price = snapshot?.price ?? null;
        const cost = p.quantity * p.averageCost;
        const value = price === null ? cost : p.quantity * price;
        const previousClose = snapshot?.previousClose ?? null;
        return {
          ...p,
          price,
          value,
          pnl: price === null ? null : value - cost,
          dayChange:
            price === null || previousClose === null ? null : p.quantity * (price - previousClose),
        };
      }),
  );

  readonly holdingsValue = computed(() => this.holdings().reduce((total, h) => total + h.value, 0));

  readonly holdingsLabel = computed(() => {
    const count = this.holdings().length;
    return `${count} ${count === 1 ? 'holding' : 'holdings'}`;
  });

  readonly valuationNote = computed(() => {
    const unpriced = this.holdings().filter((h) => h.price === null).length;
    if (unpriced === 0) {
      return 'at market price';
    }
    return unpriced === this.holdings().length ? 'at cost' : `${unpriced} at cost`;
  });

  /** Unrealised P&L across holdings that have a price; null when none do */
  readonly holdingsPnl = computed(() => {
    const priced = this.holdings().filter((h) => h.pnl !== null);
    return priced.length === 0 ? null : priced.reduce((total, h) => total + h.pnl!, 0);
  });

  /** Change since the previous close, over holdings where it is known; null when none are */
  readonly dayChange = computed(() => {
    const known = this.holdings().filter((h) => h.dayChange !== null);
    if (known.length === 0) {
      return null;
    }
    const amount = known.reduce((total, h) => total + h.dayChange!, 0);
    const start = known.reduce((total, h) => total + h.value, 0) - amount;
    return { amount, percent: start > 0 ? amount / start : 0 };
  });

  readonly pricesAsOf = computed(() => this.market()?.updatedAt ?? null);

  /** Cash committed to open BUY orders */
  readonly reservedCash = computed(() =>
    this.orders()
      .filter((o) => o.side === 'BUY' && OPEN_STATUSES.includes(o.status))
      .reduce((total, o) => total + o.totalValue, 0),
  );

  /** What a new order can spend, matching the backend's check when placing one */
  readonly buyingPower = computed(() =>
    Math.max(0, (this.account()?.cashBalance ?? 0) - this.reservedCash()),
  );

  readonly totalValue = computed(() => (this.account()?.cashBalance ?? 0) + this.holdingsValue());

  /** Largest four holdings, the rest grouped as "Other", then cash */
  readonly allocation = computed<AllocationSlice[]>(() => {
    const ranked = [...this.holdings()].sort((a, b) => b.value - a.value);
    const slices = ranked.slice(0, 4).map((h) => ({ label: h.symbol, value: h.value }));
    const other = ranked.slice(4).reduce((total, h) => total + h.value, 0);
    if (other > 0) {
      slices.push({ label: 'Other', value: other });
    }
    return [...slices, { label: 'Cash', value: this.account()?.cashBalance ?? 0 }];
  });

  ngOnInit(): void {
    this.load();
    timer(2000, 2000)
      .pipe(
        exhaustMap(() => this.dashboard.load().pipe(catchError(() => EMPTY))),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe((data) => this.show(data));
    // Prices load with the page, then refresh in step with the backend
    interval(MARKET_REFRESH_MS)
      .pipe(
        startWith(0),
        exhaustMap(() => this.dashboard.marketPrices()),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe((market) => this.market.set(market));
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.dashboard
      .load()
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (data) => {
          this.show(data);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('We couldn’t load your account. Please try again.');
          this.loading.set(false);
        },
      });
  }

  private show({ account, positions, orders }: DashboardData): void {
    this.account.set(account);
    this.positions.set(positions);
    this.orders.set(orders);
  }

  goToOrderTicket(): void {
    this.router.navigate(['/order']);
  }

  goToBlotter(): void {
    this.router.navigate(['/blotter']);
  }

  /** Deposits and withdrawals live on the Account page; this opens its cash section */
  goToCash(action: 'deposit' | 'withdraw'): void {
    this.router.navigate(['/account'], { queryParams: { section: 'cash', action } });
  }
}
