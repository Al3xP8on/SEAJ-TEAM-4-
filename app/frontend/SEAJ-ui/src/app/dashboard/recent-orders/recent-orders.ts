import {
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Router } from '@angular/router';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { Order, OrderHistory, OrderStatus } from '../../orders/orders.models';
import { DashboardService } from '../dashboard.service';
import { orderJourney } from './order-journey';

// How many of the latest orders the card shows
const RECENT_ORDER_COUNT = 5;

@Component({
  selector: 'app-recent-orders',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, HlmCardImports],
  templateUrl: './recent-orders.html',
  styleUrl: './recent-orders.css',
})
export class RecentOrdersComponent {
  private readonly router = inject(Router);
  private readonly dashboard = inject(DashboardService);
  private readonly destroyRef = inject(DestroyRef);

  /** The order whose journey is open, if any */
  readonly expanded = signal<string | null>(null);

  /** History per order, with the status it was fetched for; undefined history means loading */
  private readonly histories = signal(
    new Map<string, { status: OrderStatus; history: OrderHistory[] | null | undefined }>(),
  );

  readonly journey = computed(() => {
    const id = this.expanded();
    const order = this.orders().find((o) => o.orderId === id);
    if (!order) {
      return null;
    }
    const history = this.histories().get(order.orderId)?.history;
    if (history === undefined) {
      return { state: 'loading' as const };
    }
    if (history === null) {
      return { state: 'error' as const };
    }
    return { state: 'ready' as const, ...orderJourney(history, order) };
  });

  readonly orders = input.required<Order[]>();

  /** Newest first; ISO timestamps sort correctly as strings */
  readonly recentOrders = computed(() =>
    [...this.orders()]
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .slice(0, RECENT_ORDER_COUNT),
  );

  constructor() {
    // Fetch when an order is opened, and again if its status moves on while it's open
    effect(() => {
      const id = this.expanded();
      const order = this.orders().find((o) => o.orderId === id);
      if (!order) {
        return;
      }
      const cached = untracked(this.histories).get(order.orderId);
      if (cached?.status === order.status) {
        return;
      }
      // Record the status first so the 2-second refresh doesn't start a second request
      this.store(order.orderId, order.status, cached?.history);
      this.dashboard
        .orderHistory(order.orderId)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((history) => this.store(order.orderId, order.status, history));
    });
  }

  toggle(orderId: string): void {
    this.expanded.update((open) => (open === orderId ? null : orderId));
  }

  private store(
    orderId: string,
    status: OrderStatus,
    history: OrderHistory[] | null | undefined,
  ): void {
    this.histories.update((all) => new Map(all).set(orderId, { status, history }));
  }

  goToBlotter(): void {
    this.router.navigate(['/blotter']);
  }
}
