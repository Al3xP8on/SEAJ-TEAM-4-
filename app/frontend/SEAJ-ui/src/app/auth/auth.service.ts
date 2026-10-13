import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface LoginResponse {
  access_token?: string;
  accessToken?: string;
  token?: string;
}

export interface RegisterPayload {
  username: string;
  password: string;
  email: string | null;
  phone: string | null;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly AUTH_URL = 'http://localhost:3000/auth/login';
  private readonly ACCOUNTS_URL = 'http://localhost:8081/api/v1/accounts';

  constructor(private http: HttpClient) {}

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(this.AUTH_URL, { username, password });
  }

  register(payload: RegisterPayload): Observable<{ accountId?: string }> {
    return this.http.post<{ accountId?: string }>(this.ACCOUNTS_URL, payload);
  }

  extractToken(data: LoginResponse): string | null {
    return data?.access_token ?? data?.accessToken ?? data?.token ?? null;
  }
}
