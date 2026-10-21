import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { forkJoin } from 'rxjs';
import { Account, Position } from '../dashboard/dashboard.models';
import { Order } from '../orders/orders.models';

export type AccountStatus = Account['status'];
export interface AdminAccount extends Account {
  username: string;
  email: string | null;
  roles: string;
  lastUpdated: string;
}
export interface AccountDetails {
  positions: Position[];
  orders: Order[];
}
export interface AccountStatusHistory {
  id: number;
  previousStatus: AccountStatus;
  newStatus: AccountStatus;
  reason: string;
  administrator: string;
  changedOn: string;
}

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);

  accounts() {
    return this.http.get<AdminAccount[]>('/api/v1/admin/accounts');
  }

  changeStatus(id: number, status: AccountStatus, reason = '') {
    return this.http.put<AdminAccount>(`/api/v1/admin/accounts/${id}/status`, {
      status,
      ...(reason.trim() ? { reason: reason.trim() } : {}),
    });
  }

  history(id: number) {
    return this.http.get<AccountStatusHistory[]>(`/api/v1/admin/accounts/${id}/history`);
  }

  details(accountId: string) {
    const id = encodeURIComponent(accountId);
    return forkJoin({
      positions: this.http.get<Position[]>(`/api/v1/accounts/${id}/positions`),
      orders: this.http.get<Order[]>(`/api/v1/orders/account/${id}`),
    });
  }
}
