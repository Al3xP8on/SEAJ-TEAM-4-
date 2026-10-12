/* Client session checks do not replace server-side JWT signature verification. */
class AuthService {
    constructor({ storage, fetchImpl = (...args) => globalThis.fetch(...args), loginUrl = '/auth/login', now = Date.now } = {}) {
        this.storage = storage ?? globalThis.sessionStorage;
        this.fetchImpl = fetchImpl;
        this.loginUrl = loginUrl;
        this.now = now;
        this.storageKey = 'seaj.access_token';
        this.generation = 0;
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
            const { result } = await this.request(`${this.loginUrl}/verify`, { challenge_id: pending.id, code });
            if (pending.generation !== this.generation) throw new Error('Login was cancelled.');
            if (!this.isUsableToken(result?.access_token)) throw new Error('Authentication service returned an invalid or expired token.');
            this.storage.setItem(this.storageKey, result.access_token);
            this.pendingLogin = null;
            return result;
        } catch (error) {
            if (pending.generation === this.generation) this.storage.removeItem(this.storageKey);
            throw error;
        }
    }

    getToken() {
        return this.storage.getItem(this.storageKey);
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
        this.storage.removeItem(this.storageKey);
    }
}

if (typeof module !== 'undefined' && module.exports) module.exports = { AuthService };
