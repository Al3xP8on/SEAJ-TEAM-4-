import { Injectable, inject } from '@angular/core';
import { NotificationsService } from './notifications.service';
import { Order, OrderStatus } from '../orders/orders.models';

@Injectable({ providedIn: 'root' })
export class OrderNotificationsService {
  private readonly notifications = inject(NotificationsService);
  private readonly statuses = new Map<string, OrderStatus>();
  private initialized = false;
  observe(orders: Order[]) {
    for (const order of orders) {
      const previous = this.statuses.get(order.orderId);
      if ((this.initialized || previous !== undefined) && previous !== order.status) this.updated(order);
      this.statuses.set(order.orderId, order.status);
    }
    this.initialized = true;
  }
  placed(order: Order) {
    this.notifications.show('Order placed', `${order.side === 'SELL' ? 'Sell' : 'Buy'} ${order.quantity} ${order.ticker}.`);
    const previous = this.statuses.get(order.orderId);
    this.statuses.set(order.orderId, order.status);
    if (previous !== order.status && ['FILLED', 'REJECTED', 'CANCELLED'].includes(order.status)) this.updated(order);
  }
  private updated(order: Order) {
    const sold = order.side === 'SELL' && order.status === 'FILLED';
    const status = order.status.charAt(0) + order.status.slice(1).toLowerCase();
    let kind: 'success' | 'info' | 'error' = 'info';
    if (order.status === 'REJECTED') kind = 'error';
    else if (order.status === 'FILLED') kind = 'success';
    this.notifications.show(sold ? 'Sold successfully' : `Order ${status.toLowerCase()}`,
      `${order.quantity} ${order.ticker} · ${order.orderId}${sold ? ' · Your sell order has filled.' : ''}`,
      kind);
  }
  reset() { this.statuses.clear(); this.initialized = false; }
}
