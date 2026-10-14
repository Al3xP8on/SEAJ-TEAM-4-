import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, forkJoin, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { Account, Position } from './dashboard.models';

export interface DashboardData {
  account: Account;
  positions: Position[];
}

const ACCOUNTS_URL = '/api/v1/accounts';

/** Loads the logged-in user's account and positions from the Java API */
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
    return forkJoin({
      account: this.http.get<Account>(base),
      positions: this.http.get<Position[]>(`${base}/positions`),
    });
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
