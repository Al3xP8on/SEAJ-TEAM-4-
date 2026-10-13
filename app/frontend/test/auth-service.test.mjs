// Tests browser authentication, token storage, email verification and automatic session renewal.
// Covers expiry, rotating tokens, reload recovery, network failures and logout races with mocked requests.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { AuthService } from '../SEAJ-ui/src/app/auth/session-client.mjs';
const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
const token = payload => `${encode({ alg: 'HS256' })}.${encode({ amr: ['pwd', 'email_otp'], ...payload })}.signature`;
const challenge = { challenge_id: '00000000-0000-4000-8000-000000000001', expires_in: 300, message: 'Enter code' };
const session = (access_token, refresh_token = 'refresh-1') => ({ access_token, refresh_token });
const response = (status, result) => ({ ok: status >= 200 && status < 300, status, json: async () => result });
function setup(fetchImpl, options = {}) {
    const values = new Map();
    const storage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) };
    return { service: new AuthService({ storage, fetchImpl, now: () => 100000, ...options }), storage };
}
test('default browser fetch supports registration and two-factor login with the correct receiver', async t => {
    const jwt = token({ sub: '1', exp: 200 });
    const calls = [];
    // Browser fetch rejects the wrong receiver; the injected arrow-function mocks do not.
    t.mock.method(globalThis, 'fetch', async function (url, options) {
        if (this !== globalThis) throw new TypeError("Failed to execute 'fetch' on 'Window': Illegal invocation");
        calls.push({ url, body: JSON.parse(options.body) });
        assert.equal(options.method, 'POST');
        assert.equal(options.headers['Content-Type'], 'application/json');
        if (url === '/auth/register/verify') return response(201, { username: 'alice', account_id: 'ACC-7A3F91C2' });
        if (url === '/auth/login/verify') return response(200, session(jwt));
        return response(202, challenge);
    });
    const { service } = setup();
    const credentials = { username: 'alice', password: 'password' };
    const registration = { ...credentials, email: 'alice@example.com' };
    const verification = { challenge_id: challenge.challenge_id, code: '123456' };
    assert.deepEqual(await service.request('/auth/register', registration), { status: 202, result: challenge });
    const registered = await service.request('/auth/register/verify', verification);
    assert.equal(registered.status, 201);
    assert.equal(service.getToken(), null);
    await service.login(credentials);
    assert.equal(service.getToken(), null);
    await service.verifyLogin(verification.code);
    assert.equal(service.getToken(), jwt);
    assert.deepEqual(calls, [
        { url: '/auth/register', body: registration },
        { url: '/auth/register/verify', body: verification },
        { url: '/auth/login', body: credentials },
        { url: '/auth/login/verify', body: verification },
    ]);
});
test('username/password starts a challenge; only verified code stores a token', async () => {
    const jwt = token({ sub: '1', exp: 200 });
    const { service } = setup(async (url, options) => {
        assert.equal(options.method, 'POST');
        assert.equal(options.headers['Content-Type'], 'application/json');
        if (url === '/auth/login') {
            assert.deepEqual(JSON.parse(options.body), { username: 'alice', password: 'password' });
            return response(202, challenge);
        }
        assert.equal(url, '/auth/login/verify');
        assert.deepEqual(JSON.parse(options.body), { challenge_id: challenge.challenge_id, code: '123456' });
        return response(200, session(jwt));
    });
    assert.equal(service.getToken(), null);
    assert.deepEqual(await service.login({ username: 'alice', password: 'password' }), challenge);
    assert.equal(service.getToken(), null); assert.equal(service.isAuthenticated(), false);
    await service.verifyLogin('123456');
    assert.equal(service.getToken(), jwt); assert.equal(service.isAuthenticated(), true);
    service.logout(); assert.equal(service.storage.getItem(service.refreshStorageKey), null); assert.equal(service.getToken(), null); assert.equal(service.isAuthenticated(), false);
});
test('incorrect credentials clear an existing token and preserve failure for display', async () => {
    const { service, storage } = setup(async () => response(401, { message: 'Invalid credentials' }));
    storage.setItem(service.storageKey, token({ exp: 200 }));
    await assert.rejects(service.login({}), error => error.status === 401 && error.message === 'Invalid credentials');
    assert.equal(service.getToken(), null);
});
test('wrong email code leaves no token and allows retry of the pending challenge', async () => {
    let wrong = true;
    const { service } = setup(async url => {
        if (url === '/auth/login') return response(202, challenge);
        if (wrong) return response(401, { message: 'Invalid or expired verification code' });
        return response(200, session(token({ exp: 200 })));
    });
    await service.login({}); await assert.rejects(service.verifyLogin('000000'), /Invalid or expired/);
    assert.equal(service.getToken(), null); wrong = false;
    await service.verifyLogin('123456'); assert.equal(service.isAuthenticated(), true);
});
test('network failure is returned and no token remains', async () => {
    const error = new Error('Network unavailable'); const { service } = setup(async () => { throw error; });
    await assert.rejects(service.login({}), candidate => candidate === error); assert.equal(service.getToken(), null);
});
test('a direct token response cannot skip the email step', async () => {
    const { service } = setup(async () => response(200, session(token({ exp: 200 }))));
    await assert.rejects(service.login({}), /invalid verification challenge/); assert.equal(service.getToken(), null);
});
test('verification cannot run without a pending login', async () => {
    const { service } = setup(); await assert.rejects(service.verifyLogin('123456'), /Start login/);
});
test('expired, malformed, unsigned and not-yet-valid tokens are unauthenticated', () => {
    const { service, storage } = setup();
    for (const jwt of ['', 'bad', token({ exp: 100 }), token({}), token({ exp: '200' }), token({ exp: 200, amr: ['pwd'] }), token({ exp: 200, nbf: 150 }), `${encode({ alg: 'none' })}.${encode({ exp: 200 })}.signature`]) {
        storage.setItem(service.storageKey, jwt); assert.equal(service.isAuthenticated(), false);
    }
});
test('malformed verification success responses never store a token', async () => {
    for (const access_token of [undefined, 'bad', token({ exp: 99 })]) {
        const { service } = setup(async url => response(url === '/auth/login' ? 202 : 200, url === '/auth/login' ? challenge : session(access_token)));
        await service.login({}); await assert.rejects(service.verifyLogin('123456'), /invalid or expired/); assert.equal(service.getToken(), null);
    }
});
test('logout during pending second-factor verification prevents session restoration', async () => {
    let complete;
    const { service } = setup(url => url === '/auth/login' ? Promise.resolve(response(202, challenge)) : new Promise(resolve => { complete = resolve; }));
    await service.login({}); const pending = service.verifyLogin('123456'); service.logout();
    complete(response(200, session(token({ exp: 200 }))));
    await assert.rejects(pending, /cancelled/); assert.equal(service.getToken(), null);
});
test('logout during initial login prevents a late response becoming a pending challenge', async () => {
    let complete; const { service } = setup(() => new Promise(resolve => { complete = resolve; }));
    const pending = service.login({}); service.logout(); complete(response(202, challenge));
    await assert.rejects(pending, /cancelled/); await assert.rejects(service.verifyLogin('123456'), /Start login/);
});
test('session status updates at expiry and survives a service reload', () => {
    const { service, storage } = setup(); storage.setItem(service.storageKey, token({ exp: 200 }));
    const reloaded = new AuthService({ storage, now: () => 199999 }); assert.equal(reloaded.isAuthenticated(), true);
    reloaded.now = () => 200000; assert.equal(reloaded.isAuthenticated(), false);
});

function clock() {
    let time = 100000;
    let nextId = 0;
    const timers = new Map();
    return {
        now: () => time,
        setTimer: (callback, delay) => { const id = ++nextId; timers.set(id, { callback, delay }); return id; },
        clearTimer: id => timers.delete(id),
        timers,
        advance: milliseconds => { time += milliseconds; },
        fire: () => { const [id, timer] = timers.entries().next().value; timers.delete(id); timer.callback(); },
    };
}
async function signIn(service) {
    await service.login({ username: 'alice', password: 'password' });
    await service.verifyLogin('123456');
}
test('automatically refreshes before expiry, rotates both tokens and schedules the next renewal', async () => {
    const timer = clock();
    const calls = [];
    const { service, storage } = setup(async (url, options) => {
        calls.push(url);
        if (url.endsWith('/login')) return response(202, challenge);
        if (url.endsWith('/verify')) return response(200, session(token({ exp: 200 })));
        assert.equal(url, '/auth/refresh');
        assert.deepEqual(JSON.parse(options.body), { refresh_token: 'refresh-1' });
        return response(200, session(token({ exp: 400 }), 'refresh-2'));
    }, timer);
    await signIn(service);
    assert.equal(storage.getItem(service.refreshStorageKey), 'refresh-1');
    assert.equal([...timer.timers.values()][0].delay, 80000);
    timer.advance(80000); timer.fire(); await service.refreshPromise;
    assert.equal(service.getToken(), token({ exp: 400 }));
    assert.equal(storage.getItem(service.refreshStorageKey), 'refresh-2');
    assert.equal([...timer.timers.values()][0].delay, 190000);
    assert.deepEqual(calls, ['/auth/login', '/auth/login/verify', '/auth/refresh']);
    service.logout(); assert.equal(timer.timers.size, 0);
});
test('page reload restores automatic refresh, including when the access token expired while closed', async () => {
    for (const exp of [200, 99]) {
        const { storage } = setup(); const timer = clock(); let calls = 0;
        storage.setItem('seaj.access_token', token({ exp }));
        storage.setItem('seaj.refresh_token', 'refresh-1');
        const service = new AuthService({ storage, ...timer, fetchImpl: async () => {
            calls++; return response(200, session(token({ exp: 300 }), 'refresh-2'));
        } });
        if (exp > 100) { assert.equal(calls, 0); timer.advance(80000); timer.fire(); }
        await service.refreshPromise;
        assert.equal(calls, 1); assert.equal(service.isAuthenticated(), true);
        service.dispose(); assert.equal(timer.timers.size, 0);
    }
});
test('focus, online and visibility recovery share one refresh request after browser sleep', async () => {
    const timer = clock(); const events = new EventTarget(); const document = new EventTarget();
    document.hidden = false;
    let complete; let calls = 0;
    const { service } = setup(async url => {
        if (url.endsWith('/login')) return response(202, challenge);
        if (url.endsWith('/verify')) return response(200, session(token({ exp: 200 })));
        calls++; return new Promise(resolve => { complete = resolve; });
    }, { ...timer, eventTarget: events, documentTarget: document });
    await signIn(service); timer.advance(110000);
    events.dispatchEvent(new Event('focus')); events.dispatchEvent(new Event('online'));
    document.dispatchEvent(new Event('visibilitychange'));
    const pending = service.refresh();
    assert.equal(pending, service.refresh()); assert.equal(calls, 1);
    complete(response(200, session(token({ exp: 400 }), 'refresh-2'))); await pending;
    service.dispose(); timer.advance(300000); events.dispatchEvent(new Event('focus'));
    assert.equal(calls, 1);
});
test('expired or revoked refresh tokens clear both tokens, stop timers and request a new login', async () => {
    const timer = clock(); let expired = 0;
    const { service, storage } = setup(async url => {
        if (url.endsWith('/login')) return response(202, challenge);
        if (url.endsWith('/verify')) return response(200, session(token({ exp: 200 })));
        return response(401, { message: 'Invalid or expired refresh token' });
    }, { ...timer, onSessionExpired: () => expired++ });
    await signIn(service);
    await assert.rejects(service.refresh(), error => error.status === 401);
    assert.equal(service.getToken(), null); assert.equal(storage.getItem(service.refreshStorageKey), null);
    assert.equal(timer.timers.size, 0); assert.equal(expired, 1);
});
test('transient refresh failure preserves the session and retries without an unhandled rejection', async () => {
    const timer = clock(); let attempts = 0;
    const { service, storage } = setup(async url => {
        if (url.endsWith('/login')) return response(202, challenge);
        if (url.endsWith('/verify')) return response(200, session(token({ exp: 200 })));
        if (++attempts === 1) throw new Error('Offline');
        return response(200, session(token({ exp: 300 }), 'refresh-2'));
    }, timer);
    await signIn(service); timer.fire(); await assert.rejects(service.refreshPromise, /Offline/);
    assert.equal(storage.getItem(service.refreshStorageKey), 'refresh-1');
    assert.equal([...timer.timers.values()][0].delay, 5000);
    timer.fire(); await service.refreshPromise;
    assert.equal(storage.getItem(service.refreshStorageKey), 'refresh-2'); service.dispose();
});
test('malformed token-pair responses cannot leave a partial session or an endless refresh loop', async () => {
    for (const invalid of [session(token({ exp: 200 }), ''), { access_token: token({ exp: 200 }) }, session('bad')]) {
        const timer = clock(); let broken = false;
        const { service, storage } = setup(async url => {
            if (url.endsWith('/login')) return response(202, challenge);
            return response(200, broken ? invalid : session(token({ exp: 200 })));
        }, timer);
        await signIn(service); broken = true;
        await assert.rejects(service.refresh(), /invalid or expired/);
        assert.equal(service.getToken(), null); assert.equal(storage.getItem(service.refreshStorageKey), null);
        assert.equal(timer.timers.size, 0);
    }
});
test('logout during refresh prevents the late rotated tokens from restoring the session', async () => {
    const timer = clock(); let complete;
    const { service, storage } = setup(async url => {
        if (url.endsWith('/login')) return response(202, challenge);
        if (url.endsWith('/verify')) return response(200, session(token({ exp: 200 })));
        return new Promise(resolve => { complete = resolve; });
    }, timer);
    await signIn(service); const pending = service.refresh(); service.logout();
    complete(response(200, session(token({ exp: 300 }), 'refresh-2')));
    await assert.rejects(pending, /cancelled/);
    assert.equal(service.getToken(), null); assert.equal(storage.getItem(service.refreshStorageKey), null);
    assert.equal(timer.timers.size, 0);
});
test('a failed old refresh cannot clear a newly verified login', async () => {
    const timer = clock(); let complete;
    const { service } = setup(async url => {
        if (url.endsWith('/login')) return response(202, challenge);
        if (url.endsWith('/verify')) return response(200, session(token({ exp: 300 }), 'new-login-refresh'));
        return new Promise(resolve => { complete = resolve; });
    }, timer);
    await signIn(service); const pending = service.refresh(); await signIn(service);
    complete(response(401, { message: 'Revoked' })); await assert.rejects(pending);
    assert.equal(service.isAuthenticated(), true);
    assert.equal(service.storage.getItem(service.refreshStorageKey), 'new-login-refresh'); service.dispose();
});
