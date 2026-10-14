import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { catchError, forkJoin, of } from 'rxjs';
import { Account, Position } from '../dashboard/dashboard.models';
import {
  DailyPrice,
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
  setup() {
    return forkJoin({
      accounts: this.http.get<TradingAccount[]>('/api/v1/accounts'),
      instruments: this.http.get<Instrument[]>('/api/v1/instruments?tradable=true'),
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
  priceHistory(symbol: string) {
    return this.http.get<DailyPrice[]>(`/api/v1/price-history/${encodeURIComponent(symbol)}`);
  }
  place(request: OrderRequest) {
    return this.http.post<Order>('/api/v1/orders', request);
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
