import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, forkJoin, map, switchMap, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { Account, Position } from './dashboard.models';
import { Order } from '../orders/orders.models';

export interface DashboardData {
  account: Account;
  positions: Position[];
  orders: Order[];
}

const ACCOUNTS_URL = '/api/v1/accounts';
const ORDERS_URL = '/api/v1/orders/account';

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
