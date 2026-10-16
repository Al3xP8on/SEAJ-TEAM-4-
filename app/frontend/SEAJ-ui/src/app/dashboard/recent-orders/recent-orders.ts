import { Component, computed, inject, input } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Router } from '@angular/router';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { Order } from '../../orders/orders.models';

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

  readonly orders = input.required<Order[]>();

  /** Newest first; ISO timestamps sort correctly as strings */
  readonly recentOrders = computed(() =>
    [...this.orders()]
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .slice(0, RECENT_ORDER_COUNT),
  );

  /** Full order history, with each order's timeline, lives on the Orders page */
  goToOrders(): void {
    this.router.navigate(['/order']);
  }
}
