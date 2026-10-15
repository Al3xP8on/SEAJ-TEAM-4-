import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, exhaustMap, timer } from 'rxjs';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmTableImports } from '@spartan-ng/helm/table';
import { Router } from '@angular/router';
import { Account, Position } from './dashboard.models';
import { Order } from '../orders/orders.models';
import { DashboardData, DashboardService } from './dashboard.service';
import { AllocationChartComponent, AllocationSlice } from './allocation-chart/allocation-chart';
import { PortfolioCheckComponent } from './portfolio-check/portfolio-check';

// How many of the latest orders the Recent orders card shows
const RECENT_ORDER_COUNT = 5;

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
    HlmCardImports,
    HlmTableImports,
    AllocationChartComponent,
    PortfolioCheckComponent,
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
  readonly loading = signal(true);
  readonly error = signal('');

  readonly holdings = computed(() =>
    this.positions()
      .filter((p) => HELD_STATUSES.includes(p.status) && p.quantity > 0)
      .map((p) => ({ ...p, value: p.quantity * p.averageCost })),
  );

  readonly holdingsValue = computed(() => this.holdings().reduce((total, h) => total + h.value, 0));

  readonly holdingsLabel = computed(() => {
    const count = this.holdings().length;
    return `${count} ${count === 1 ? 'holding' : 'holdings'}`;
  });

  /** Newest first; ISO timestamps sort correctly as strings */
  readonly recentOrders = computed(() =>
    [...this.orders()]
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .slice(0, RECENT_ORDER_COUNT),
  );

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
}
