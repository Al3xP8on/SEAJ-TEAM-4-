import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, forkJoin, map, of, switchMap, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { Account, Position } from './dashboard.models';
import { Instrument, MarketChart, MarketOverview, Order } from '../orders/orders.models';

export interface DashboardData {
  account: Account;
  positions: Position[];
  orders: Order[];
}

const ACCOUNTS_URL = '/api/v1/accounts';
const ORDERS_URL = '/api/v1/orders/account';
const MARKET_URL = '/api/v1/market-data/overview';
const BARS_URL = '/api/v1/market-data';
const INSTRUMENTS_URL = '/api/v1/instruments';

/** Loads the logged-in user's account, positions and orders from the Java API */
@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  load(): Observable<DashboardData> {
    const accountId = accountIdFromToken(this.auth.getToken());
    if (!accountId) {
      return throwError(() => new Error('No active session'));
    }
    const base = `${ACCOUNTS_URL}/${encodeURIComponent(accountId)}`;
    // Orders are looked up by the ACC- id, which only the account response carries
    return this.http.get<Account>(base).pipe(
      switchMap((account) =>
        forkJoin({
          positions: this.http.get<Position[]>(`${base}/positions`),
          orders: this.http.get<Order[]>(`${ORDERS_URL}/${encodeURIComponent(account.accountId)}`),
        }).pipe(map((rest) => ({ account, ...rest }))),
      ),
    );
  }

  /**
   * Latest prices for every tradable instrument. The backend caches Alpaca for all users,
   * so polling this adds no Alpaca traffic. Failure only means no live prices, never a broken page.
   */
  marketPrices(): Observable<MarketOverview | null> {
    return this.http.get<MarketOverview>(MARKET_URL).pipe(catchError(() => of(null)));
  }

  /** Company names by symbol, for labelling holdings; empty if unavailable */
  instrumentNames(): Observable<Map<string, string>> {
    return this.http.get<Instrument[]>(INSTRUMENTS_URL).pipe(
      map((instruments) => new Map(instruments.map((i) => [i.symbol, i.name]))),
      catchError(() => of(new Map<string, string>())),
    );
  }

  /** Price history for one symbol, cached by the backend (1 minute for 1Min bars, 5 for 1Day) */
  bars(symbol: string, timeframe: '1Min' | '1Day'): Observable<MarketChart | null> {
    return this.http
      .get<MarketChart>(`${BARS_URL}/${encodeURIComponent(symbol)}/bars`, { params: { timeframe } })
      .pipe(catchError(() => of(null)));
  }
}

/** The access token's subject is the account's numeric id, which the account endpoints accept */
export function accountIdFromToken(token: string | null): string | null {
  const payload = token?.split('.')[1];
  if (!payload) {
    return null;
  }
  try {
    const claims = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
    return claims.sub != null ? String(claims.sub) : null;
  } catch {
    return null;
  }
}
