// Adapts the existing session client to Angular HttpClient and observable form actions.
// Preserves email verification, sessionStorage, automatic rotation and local logout behavior.
import { Injectable, InjectionToken, inject, signal, OnDestroy } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { defer, firstValueFrom } from 'rxjs';
import { AuthService as SessionClient, Challenge } from './session-client.mjs';

export const AUTH_API_URL = new InjectionToken<string>('AUTH_API_URL', {
  providedIn: 'root', factory: () => '/auth',
});
export interface RegisterPayload { username: string; password: string; email: string; name?: string; phone?: string; }
export type AccountRole = 'USER' | 'ADMIN';
export interface RegisteredUser { username: string; account_id: string; email_verified: boolean; }

@Injectable({ providedIn: 'root' })
export class AuthService implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(AUTH_API_URL).replace(/\/$/, '');
  readonly sessionExpired = signal(false);
  private readonly client = new SessionClient({
    loginUrl: `${this.baseUrl}/login`,
    onSessionExpired: () => this.sessionExpired.set(true),
    fetchImpl: async (url, options) => {
      try {
        const response = await firstValueFrom(this.http.request(options.method ?? 'POST', url, {
          body: JSON.parse(options.body as string),
          headers: { 'Content-Type': 'application/json' },
          observe: 'response',
        }));
        return { ok: response.ok, status: response.status, json: async () => response.body };
      } catch (error) {
        if (!(error instanceof HttpErrorResponse)) throw error;
        return { ok: false, status: error.status, json: async () => error.error };
      }
    },
  });
  login(username: string, password: string) {
    return defer(() => {
      this.sessionExpired.set(false);
      return this.client.login({ username, password });
    });
  }
  verifyLogin(code: string) { return defer(() => this.client.verifyLogin(code)); }
  register(payload: RegisterPayload) {
    return defer(async () => {
      const { status, result } = await this.client.request(`${this.baseUrl}/register`, payload);
      const challenge = result as Challenge;
      if (status !== 202 || typeof challenge?.challenge_id !== 'string'
        || !Number.isFinite(challenge.expires_in) || challenge.expires_in <= 0) {
        throw new Error('Authentication service returned an invalid verification challenge.');
      }
      return challenge;
    });
  }
  registerAdmin(payload: RegisterPayload, role: AccountRole) {
    return defer(async () => {
      const { status, result } = await this.client.request(`${this.baseUrl}/admin/register`, {
        ...payload,
        role,
      });
      const challenge = result as Challenge;
      if (status !== 202 || typeof challenge?.challenge_id !== 'string'
        || !Number.isFinite(challenge.expires_in) || challenge.expires_in <= 0) {
        throw new Error('Authentication service returned an invalid verification challenge.');
      }
      return challenge;
    });
  }
  verifyRegistration(challengeId: string, code: string) {
    return defer(async () => {
      const { status, result } = await this.client.request(`${this.baseUrl}/register/verify`, {
        challenge_id: challengeId, code,
      });
      const user = result as RegisteredUser;
      if (status !== 201 || typeof user?.account_id !== 'string' || typeof user.username !== 'string') {
        throw new Error('Authentication service returned an invalid registration response.');
      }
      return user;
    });
  }
  getToken() { return this.client.getToken(); }
  isAuthenticated() { return this.client.isAuthenticated(); }
  refresh() { return defer(() => this.client.refresh()); }
  logout() { this.client.logout(); this.sessionExpired.set(false); }
  ngOnDestroy() { this.client.dispose(); }
}
