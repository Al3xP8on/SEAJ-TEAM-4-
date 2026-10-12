/* Client session checks do not replace server-side JWT signature verification. */
class AuthService {
    constructor({ storage, fetchImpl = globalThis.fetch, loginUrl = '/auth/login', now = Date.now } = {}) {
        this.storage = storage ?? globalThis.sessionStorage;
        this.fetchImpl = fetchImpl;
        this.loginUrl = loginUrl;
        this.now = now;
        this.storageKey = 'seaj.access_token';
        this.generation = 0;
    }

    async login(credentials) {
        this.logout();
        const generation = this.generation;
        try {
            const response = await this.fetchImpl(this.loginUrl, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(credentials),
            });
            if (!response.ok) {
                let body;
                try { body = await response.json(); } catch { /* Non-JSON error response. */ }
                const error = new Error(typeof body?.message === 'string' ? body.message : 'Login failed. Please try again.');
                error.status = response.status;
                error.details = body;
                throw error;
            }
            const result = await response.json();
            if (!this.isUsableToken(result.access_token)) {
                throw new Error('Authentication service returned an invalid or expired token.');
            }
            if (generation !== this.generation) {
                throw new Error('Login was cancelled.');
            }
            this.storage.setItem(this.storageKey, result.access_token);
            return result;
        } catch (error) {
            if (generation === this.generation) this.storage.removeItem(this.storageKey);
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
        this.storage.removeItem(this.storageKey);
    }
}

if (typeof module !== 'undefined' && module.exports) module.exports = { AuthService };
