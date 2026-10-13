// Manages tab-scoped login, email verification and automatic access/refresh token renewal.
// Stores tokens in sessionStorage; protected APIs verify token signatures and session validity.
class AuthService {
    constructor({ storage, fetchImpl = (...args) => globalThis.fetch(...args), loginUrl = '/auth/login', now = Date.now,
        setTimer = (fn, delay) => setTimeout(fn, delay), clearTimer = id => clearTimeout(id),
        eventTarget = globalThis.window, documentTarget = globalThis.document, onSessionExpired = () => {} } = {}) {
        this.storage = storage ?? globalThis.sessionStorage;
        this.fetchImpl = fetchImpl;
        this.loginUrl = loginUrl;
        this.now = now;
        this.storageKey = 'seaj.access_token';
        this.refreshStorageKey = 'seaj.refresh_token';
        this.generation = 0;
        this.setTimer = setTimer;
        this.clearTimer = clearTimer;
        this.onSessionExpired = onSessionExpired;
        this.eventTarget = eventTarget;
        this.documentTarget = documentTarget;
        this.resume = () => {
            if (!this.documentTarget?.hidden) this.resumeSession();
        };
        for (const event of ['focus', 'pageshow', 'online']) this.eventTarget?.addEventListener(event, this.resume);
        this.documentTarget?.addEventListener('visibilitychange', this.resume);
        this.resumeSession();
    }

    async request(url, body) {
        const response = await this.fetchImpl(url, {
            method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
        });
        let result;
        try { result = await response.json(); } catch { /* Non-JSON response. */ }
        if (!response.ok) {
            const error = new Error(typeof result?.message === 'string' ? result.message : 'Authentication failed. Please try again.');
            error.status = response.status;
            error.details = result;
            throw error;
        }
        return { status: response.status, result };
    }

    async login(credentials) {
        this.logout();
        const generation = this.generation;
        const { status, result } = await this.request(this.loginUrl, credentials);
        if (generation !== this.generation) throw new Error('Login was cancelled.');
        if (status !== 202 || typeof result?.challenge_id !== 'string' || !Number.isFinite(result.expires_in) || result.expires_in <= 0) {
            throw new Error('Authentication service returned an invalid verification challenge.');
        }
        this.pendingLogin = { id: result.challenge_id, generation };
        return result;
    }

    async verifyLogin(code) {
        const pending = this.pendingLogin;
        if (!pending) throw new Error('Start login before entering a verification code.');
        try {
            const { status, result } = await this.request(`${this.loginUrl}/verify`, { challenge_id: pending.id, code });
            if (pending.generation !== this.generation) throw new Error('Login was cancelled.');
            this.storeSession(status, result);
            this.pendingLogin = null;
            return result;
        } catch (error) {
            if (pending.generation === this.generation) this.clearTokens();
            throw error;
        }
    }

    getToken() {
        return this.storage.getItem(this.storageKey);
    }

    storeSession(status, result) {
        if (status !== 200 || !this.isUsableToken(result?.access_token)
            || typeof result.refresh_token !== 'string' || !result.refresh_token.trim() || result.refresh_token.length > 256) {
            throw new Error('Authentication service returned an invalid or expired session.');
        }
        try {
            this.storage.setItem(this.refreshStorageKey, result.refresh_token);
            this.storage.setItem(this.storageKey, result.access_token);
        } catch (error) {
            this.clearTokens();
            throw error;
        }
        this.scheduleRefresh();
    }

    tokenExpiry() {
        try {
            const payload = this.getToken().split('.')[1];
            const exp = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/'))).exp;
            return Number.isFinite(exp) ? exp * 1000 : 0;
        } catch { return 0; }
    }

    scheduleRefresh(delay) {
        this.clearTimer(this.refreshTimer);
        if (!this.storage.getItem(this.refreshStorageKey)) return;
        const remaining = this.tokenExpiry() - this.now();
        // Refresh 30 seconds early, or 20% early for short-lived test tokens.
        const wait = delay ?? Math.max(0, remaining - Math.min(30000, remaining / 5));
        this.refreshTimer = this.setTimer(() => {
            void this.refresh().catch(() => {}); // refresh handles expiry and retry scheduling.
        }, Math.min(wait, 2147483647));
        this.refreshTimer?.unref?.();
    }

    resumeSession() {
        if (!this.storage.getItem(this.refreshStorageKey)) return;
        if (!this.isAuthenticated() || this.tokenExpiry() - this.now() <= 30000) {
            void this.refresh().catch(() => {});
        } else {
            this.scheduleRefresh();
        }
    }

    refresh() {
        // Rotation consumes the old token; share one request among simultaneous callers.
        if (this.refreshPromise) return this.refreshPromise;
        const refreshToken = this.storage.getItem(this.refreshStorageKey);
        if (!refreshToken) return Promise.reject(new Error('Please log in again.'));
        const generation = this.generation;
        this.clearTimer(this.refreshTimer);
        const pending = (async () => {
            try {
                const { status, result } = await this.request(this.loginUrl.replace(/\/login$/, '/refresh'), { refresh_token: refreshToken });
                if (generation !== this.generation) throw new Error('Session refresh was cancelled.');
                try {
                    this.storeSession(status, result);
                } catch (error) {
                    error.invalidSession = true;
                    throw error;
                }
                return result;
            } catch (error) {
                if (generation === this.generation) {
                    if (error.invalidSession || [400, 401, 403].includes(error.status)) {
                        this.logout();
                        this.onSessionExpired();
                    } else {
                        // Preserve the rotating token during transient network/server failures.
                        this.scheduleRefresh(5000);
                    }
                }
                throw error;
            } finally {
                if (this.refreshPromise === pending) this.refreshPromise = null;
            }
        })();
        this.refreshPromise = pending;
        return pending;
    }

    isUsableToken(token) {
        try {
            if (typeof token !== 'string') return false;
            const parts = token.split('.');
            if (parts.length !== 3 || parts.some(part => !/^[A-Za-z0-9_-]+$/.test(part))) return false;
            const decode = part => JSON.parse(atob(part.replace(/-/g, '+').replace(/_/g, '/')));
            const header = decode(parts[0]);
            const payload = decode(parts[1]);
            return typeof header.alg === 'string' && header.alg.toLowerCase() !== 'none'
                && Array.isArray(payload.amr) && payload.amr.includes('pwd') && payload.amr.includes('email_otp')
                && Number.isFinite(payload.exp) && payload.exp * 1000 > this.now()
                && (payload.nbf === undefined || (Number.isFinite(payload.nbf) && payload.nbf * 1000 <= this.now()));
        } catch {
            return false;
        }
    }

    isAuthenticated() {
        return this.isUsableToken(this.getToken());
    }

    logout() {
        this.generation += 1;
        this.pendingLogin = null;
        this.refreshPromise = null;
        this.clearTokens();
    }

    clearTokens() {
        this.clearTimer(this.refreshTimer);
        this.storage.removeItem(this.storageKey);
        this.storage.removeItem(this.refreshStorageKey);
    }

    dispose() {
        this.generation += 1;
        this.clearTimer(this.refreshTimer);
        for (const event of ['focus', 'pageshow', 'online']) this.eventTarget?.removeEventListener(event, this.resume);
        this.documentTarget?.removeEventListener('visibilitychange', this.resume);
    }
}

export { AuthService };
