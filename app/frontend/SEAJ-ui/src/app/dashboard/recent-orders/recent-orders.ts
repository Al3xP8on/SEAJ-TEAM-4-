import { Component, computed, inject, input } from '@angular/core';
import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Router } from '@angular/router';
import { HlmCardImports } from '@spartan-ng/helm/card';
import { HlmButton } from '@spartan-ng/helm/button';
import { HlmBadge } from '@spartan-ng/helm/badge';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { lucideArrowRight } from '@ng-icons/lucide';
import { Order, OrderStatus } from '../../orders/orders.models';

// How many of the latest orders the card lists; about five show before it scrolls
const RECENT_ORDER_COUNT = 20;

type BadgeVariant = 'default' | 'secondary' | 'destructive' | 'outline';

const STATUS_BADGES: Record<OrderStatus, BadgeVariant> = {
  NEW: 'secondary',
  PENDING: 'secondary',
  FILLED: 'default',
  REJECTED: 'destructive',
  CANCELLED: 'outline',
};

@Component({
  selector: 'app-recent-orders',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, HlmCardImports, HlmButton, HlmBadge, NgIcon],
  providers: [provideIcons({ lucideArrowRight })],
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

  /** The status is spelled out in the badge; colour only reinforces it */
  badgeFor(status: OrderStatus): BadgeVariant {
    return STATUS_BADGES[status];
  }

  /** Order history keeps active orders and completed trades together. */
  goToOrders(): void {
    this.router.navigate(['/order'], { fragment: 'order-history' });
  }
}
