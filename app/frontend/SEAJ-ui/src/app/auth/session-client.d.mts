// Types the preserved browser session client for Angular without changing its runtime logic.
// Describes two-factor challenges, token pairs and injectable browser dependencies.
export interface Challenge { challenge_id: string; expires_in: number; message: string; }
export interface TokenPair { access_token: string; refresh_token: string; expires_in: number; token_type: string; }
export interface SessionOptions {
  storage?: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;
  fetchImpl?: (url: string, options: RequestInit) => Promise<{
    ok: boolean; status: number; json(): Promise<unknown>;
  }>;
  loginUrl?: string;
  now?: () => number;
  setTimer?: (callback: () => void, delay: number) => unknown;
  clearTimer?: (timer: unknown) => void;
  eventTarget?: EventTarget;
  documentTarget?: Document;
  onSessionExpired?: () => void;
}
export class AuthService {
  constructor(options?: SessionOptions);
  request(url: string, body: unknown): Promise<{ status: number; result: unknown }>;
  login(credentials: { username: string; password: string }): Promise<Challenge>;
  verifyLogin(code: string): Promise<TokenPair>;
  refresh(): Promise<TokenPair>;
  getToken(): string | null;
  isAuthenticated(): boolean;
  logout(): void;
  dispose(): void;
}
