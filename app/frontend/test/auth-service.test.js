const { test } = require('node:test');
const assert = require('node:assert/strict');
const { AuthService } = require('../Login/auth-service');
const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
const token = payload => `${encode({ alg: 'HS256' })}.${encode({ amr: ['pwd', 'email_otp'], ...payload })}.signature`;
const challenge = { challenge_id: '00000000-0000-4000-8000-000000000001', expires_in: 300, message: 'Enter code' };
const response = (status, result) => ({ ok: status >= 200 && status < 300, status, json: async () => result });
function setup(fetchImpl) {
    const values = new Map();
    const storage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) };
    return { service: new AuthService({ storage, fetchImpl, now: () => 100000 }), storage };
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
        if (url === '/auth/login/verify') return response(200, { access_token: jwt });
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
        return response(200, { access_token: jwt });
    });
    assert.equal(service.getToken(), null);
    assert.deepEqual(await service.login({ username: 'alice', password: 'password' }), challenge);
    assert.equal(service.getToken(), null); assert.equal(service.isAuthenticated(), false);
    await service.verifyLogin('123456');
    assert.equal(service.getToken(), jwt); assert.equal(service.isAuthenticated(), true);
    service.logout(); assert.equal(service.getToken(), null); assert.equal(service.isAuthenticated(), false);
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
        return response(200, { access_token: token({ exp: 200 }) });
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
    const { service } = setup(async () => response(200, { access_token: token({ exp: 200 }) }));
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
        const { service } = setup(async url => response(url === '/auth/login' ? 202 : 200, url === '/auth/login' ? challenge : { access_token }));
        await service.login({}); await assert.rejects(service.verifyLogin('123456'), /invalid or expired/); assert.equal(service.getToken(), null);
    }
});
test('logout during pending second-factor verification prevents session restoration', async () => {
    let complete;
    const { service } = setup(url => url === '/auth/login' ? Promise.resolve(response(202, challenge)) : new Promise(resolve => { complete = resolve; }));
    await service.login({}); const pending = service.verifyLogin('123456'); service.logout();
    complete(response(200, { access_token: token({ exp: 200 }) }));
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
