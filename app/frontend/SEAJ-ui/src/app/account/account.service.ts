import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { forkJoin } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { accountIdFromToken } from '../dashboard/dashboard.service';
import { Account } from '../dashboard/dashboard.models';
export interface Bank { id?: string; bank_name: string; holder_name: string; account_last4: string; routing_last4: string; kind?: string; expiry_month?: number; expiry_year?: number; }
export interface BankInput { holder_name: string; card_number: string; expiry_month: number; expiry_year: number; }
export interface Transfer { id: string; action: 'deposit' | 'withdraw'; amount: string; bank_name: string; account_last4: string; created_at: string; status: string; }
export interface Settings { phone: string | null; bank: Bank | null; cards?: Bank[]; cash_balance: string; transfers: Transfer[]; }
export interface Profile { username: string; email: string; name: string; email_verified: boolean; }
@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private headers() { return { Authorization: `Bearer ${this.auth.getToken()}` }; }
  private url() {
    const id = accountIdFromToken(this.auth.getToken());
    if (!id) throw new Error('No active session');
    return `/api/v1/accounts/${encodeURIComponent(id)}`;
  }
  load() { return forkJoin({ account: this.http.get<Account>(this.url()), profile: this.http.get<Profile>('/auth/me', { headers: this.headers() }), settings: this.http.get<Settings>('/auth/settings', { headers: this.headers() }) }); }
  savePhone(phone: string) { return this.http.post<Settings>('/auth/profile', { phone }, { headers: this.headers() }); }
  saveBank(bank: BankInput) { return this.http.post<Settings>('/auth/bank', bank, { headers: this.headers() }); }
  removeCard(card_id: string) { return this.http.post<Settings>('/auth/cards/remove', { card_id }, { headers: this.headers() }); }
  startDeletion() { return this.http.post<{ challenge_id: string }>('/auth/account/delete/start', {}, { headers: this.headers() }); }
  verifyDeletion(challenge_id: string, code: string) { return this.http.post<{ deletion_token: string }>('/auth/account/delete/verify', { challenge_id, code }, { headers: this.headers() }); }
  confirmDeletion(deletion_token: string) { return this.http.post('/auth/account/delete/confirm', { deletion_token }, { headers: this.headers() }); }
  cash(action: 'deposit' | 'withdraw', amount: string, request_id: string, card?: BankInput, card_id?: string, save_card = false) { return this.http.post<Settings>('/auth/transfers', { action, amount, request_id, card, card_id, save_card }, { headers: this.headers() }); }
  changeEmail(email: string) { return this.http.post<{ challenge_id: string }>('/auth/email', { email }, { headers: this.headers() }); }
  verifyEmail(challenge_id: string, code: string) { return this.http.post<{ email: string }>('/auth/email/verify', { challenge_id, code }, { headers: this.headers() }); }
  password(current_password: string, new_password: string) {
    return this.http.post('/auth/password', { current_password, new_password }, { headers: this.headers() });
  }
}
