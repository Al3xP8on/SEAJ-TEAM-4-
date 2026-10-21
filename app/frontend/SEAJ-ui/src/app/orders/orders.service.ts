import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { OrderNotificationsService } from '../notifications/order-notifications.service';
import { catchError, forkJoin, of, tap } from 'rxjs';
import { Account, Position } from '../dashboard/dashboard.models';
import {
  DailyPrice,
  MarketOverview,
  MarketChart,
  MarketTimeframe,
  Instrument,
  Order,
  OrderHistory,
  OrderRequest,
  Quote,
  TradingAccount,
} from './orders.models';

@Injectable({ providedIn: 'root' })
export class OrdersService {
  private readonly http = inject(HttpClient);
  private readonly notifications = inject(OrderNotificationsService);
  setup() {
    return forkJoin({
      accounts: this.http.get<TradingAccount[]>('/api/v1/accounts'),
      instruments: this.http.get<Instrument[]>('/api/v1/instruments?tradable=true'),
    });
  }
  managementSetup() {
    return forkJoin({
      accounts: this.http.get<TradingAccount[]>('/api/v1/accounts'),
      instruments: of([] as Instrument[]),
    });
  }
  orders() {
    return this.http.get<Order[]>('/api/v1/orders');
  }
  portfolio(accountId: string) {
    const url = `/api/v1/accounts/${encodeURIComponent(accountId)}`;
    return forkJoin({
      account: this.http.get<Account>(url),
      positions: this.http.get<Position[]>(`${url}/positions`),
    });
  }
  quote(symbol: string) {
    return this.http
      .get<Quote | null>(`/api/v1/instruments/${encodeURIComponent(symbol)}/quote`)
      .pipe(catchError(() => of(null)));
  }
  marketOverview() {
    return this.http
      .get<MarketOverview>('/api/v1/market-data/overview')
      .pipe(catchError(() => of(null)));
  }
  priceHistory(symbol: string) {
    return this.http.get<DailyPrice[]>(`/api/v1/price-history/${encodeURIComponent(symbol)}`);
  }
  marketBars(symbol: string, timeframe: MarketTimeframe) {
    return this.http.get<MarketChart>(`/api/v1/market-data/${encodeURIComponent(symbol)}/bars`, {
      params: { timeframe },
    });
  }
  place(request: OrderRequest) {
    return this.http
      .post<Order>('/api/v1/orders', request)
      .pipe(tap((order) => this.notifications.placed(order)));
  }
  cancel(id: string) {
    return this.http.put<Order>(`/api/v1/orders/${encodeURIComponent(id)}/cancel`, {});
  }
  reject(id: string, reason: string) {
    return this.http.put<Order>(
      `/api/v1/orders/${encodeURIComponent(id)}/reject`,
      {},
      { params: { reason } },
    );
  }
  history(id: string) {
    return this.http.get<OrderHistory[]>(`/api/v1/orders/${encodeURIComponent(id)}/history`);
  }
}
