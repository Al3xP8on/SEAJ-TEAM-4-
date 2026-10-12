const { test } = require('node:test');
const assert = require('node:assert/strict');
const { AuthService } = require('./auth-service');
const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
const token = payload => `${encode({ alg: 'HS256' })}.${encode(payload)}.signature`;
function setup(fetchImpl) {
    const values = new Map();
    const storage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) };
    const service = new AuthService({ storage, fetchImpl, now: () => 100000 });
    return { service, storage };
}
test('login sends credentials and stores the returned access token', async () => {
    const jwt = token({ sub: 1, exp: 200 });
    const { service } = setup(async (url, options) => {
        assert.equal(url, '/auth/login');
        assert.equal(options.method, 'POST');
        assert.equal(options.headers['Content-Type'], 'application/json');
        assert.deepEqual(JSON.parse(options.body), { username: 'alice', password: 'password' });
        return { ok: true, json: async () => ({ access_token: jwt }) };
    });
    assert.equal(service.getToken(), null);
    assert.equal(service.isAuthenticated(), false);
    assert.deepEqual(await service.login({ username: 'alice', password: 'password' }), { access_token: jwt });
    assert.equal(service.getToken(), jwt);
    assert.equal(service.isAuthenticated(), true);
    service.logout();
    assert.equal(service.getToken(), null);
    assert.equal(service.isAuthenticated(), false);
});
test('HTTP failure clears an existing token and preserves the error for display', async () => {
    const { service, storage } = setup(async () => ({ ok: false, status: 401, json: async () => ({ message: 'Invalid credentials' }) }));
    storage.setItem(service.storageKey, token({ exp: 200 }));
    await assert.rejects(service.login({}), error => error.status === 401 && error.message === 'Invalid credentials');
    assert.equal(service.getToken(), null);
});
test('network failure is returned and no token remains', async () => {
    const error = new Error('Network unavailable');
    const { service } = setup(async () => { throw error; });
    await assert.rejects(service.login({}), candidate => candidate === error);
    assert.equal(service.getToken(), null);
});
test('expired, malformed, unsigned and not-yet-valid tokens are unauthenticated', () => {
    const { service, storage } = setup();
    for (const jwt of [null, '', 'bad', token({ exp: 100 }), token({}), token({ exp: '200' }), token({ exp: 200, nbf: 150 }), `${encode({ alg: 'none' })}.${encode({ exp: 200 })}.signature`]) {
        if (jwt === null) storage.removeItem(service.storageKey);
        else storage.setItem(service.storageKey, jwt);
        assert.equal(service.isAuthenticated(), false);
    }
});
test('malformed success responses never store a token', async () => {
    for (const access_token of [undefined, 'bad', token({ exp: 99 })]) {
        const { service } = setup(async () => ({ ok: true, json: async () => ({ access_token }) }));
        await assert.rejects(service.login({}), /invalid or expired/);
        assert.equal(service.getToken(), null);
    }
});
test('logout during pending login prevents the late response restoring the session', async () => {
    let complete;
    const { service } = setup(() => new Promise(resolve => { complete = resolve; }));
    const pending = service.login({});
    service.logout();
    complete({ ok: true, json: async () => ({ access_token: token({ exp: 200 }) }) });
    await assert.rejects(pending, /cancelled/);
    assert.equal(service.getToken(), null);
});
test('session status updates when the token expires and survives a service reload', () => {
    const { service, storage } = setup();
    storage.setItem(service.storageKey, token({ exp: 200 }));
    const reloaded = new AuthService({ storage, now: () => 199999 });
    assert.equal(reloaded.isAuthenticated(), true);
    reloaded.now = () => 200000;
    assert.equal(reloaded.isAuthenticated(), false);
});
