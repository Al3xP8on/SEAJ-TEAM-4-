// Exercises registration, email verification, JWTs and refresh/logout through real HTTP requests.
// Uses isolated PostgreSQL schemas and fake email delivery to test validation, concurrency and persistence.
import { Test } from '@nestjs/testing';
import type { INestApplication } from '@nestjs/common';
import { ServiceUnavailableException } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { JwtService } from '@nestjs/jwt';
import { DataSource } from 'typeorm';
import request from 'supertest';
import Ajv from 'ajv';
import addFormats from 'ajv-formats';
import { load } from 'js-yaml';
import { SwaggerModule } from '@nestjs/swagger';
import type { OpenAPIObject } from '@nestjs/swagger';
import { readFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
import { AuthModule } from '../src/auth/auth.module';
import { User } from '../src/users/user.entity';
import { EmailService } from '../src/auth/email.service';
import { EmailChallenge } from '../src/auth/email-challenge.entity';
import { EmailVerification1791849600000 } from '../src/migrations/add-email-verification.migration';
import { AuthSession } from '../src/auth/auth-session.entity';
import { AuthSessions1791763200000 } from '../src/migrations/create-auth-sessions.migration';
import { AccountRoles1791896400000 } from '../src/migrations/align-account-roles.migration';
import { configureHttp } from '../src/http.config';

// Each run uses an isolated schema; never import AppModule's application DB config.
const url = process.env.AUTH_TEST_DATABASE_URL;
if (!url)
  throw new Error(
    'Set AUTH_TEST_DATABASE_URL to a disposable PostgreSQL test database',
  );
describe('Authentication HTTP contract (PostgreSQL)', () => {
  let app: INestApplication;
  let admin: DataSource;
  let db: DataSource;
  let jwt: JwtService;
  const schema = `auth_test_${randomUUID().replace(/-/g, '')}`;
  const contract = load(readFileSync('../../../docs/auth-service.yml', 'utf8')) as OpenAPIObject;
  const ajv = new Ajv({ strict: false });
  addFormats(ajv);
  ajv.addSchema({ $id: 'auth-contract', components: contract.components });
  const mail: { email: string; code: string; purpose: string }[] = [];
  const sendCode = jest.fn(
    async (email: string, code: string, purpose: string) => {
      mail.push({ email, code, purpose });
    },
  );
  const registration = {
    username: 'newalice',
    email: 'alice@example.com',
    password: 'SecurePass123!',
  };
  beforeAll(async () => {
    admin = await new DataSource({ type: 'postgres', url }).initialize();
    await admin.query(`CREATE SCHEMA "${schema}"`);
    await admin.query(
      `CREATE TYPE "${schema}".account_status AS ENUM ('ACTIVE', 'SUSPENDED', 'CLOSED')`,
    );
    const runner = admin.createQueryRunner();
    await runner.connect();
    try {
      await runner.query(`SET search_path TO "${schema}"`);
      await runner.query(
        readFileSync('../../../infra/db/tables/02-accounts.sql', 'utf8'),
      );
    } finally {
      await runner.release();
    }
    const module = await Test.createTestingModule({
      imports: [
        ConfigModule.forRoot({
          ignoreEnvFile: true,
          skipProcessEnv: true,
          load: [() => ({
            JWT_SECRET: 'test-only-secret-with-at-least-32-bytes',
            JWT_EXPIRY: '120',
            JWT_REFRESH_EXPIRY: '600',
          })],
        }),
        TypeOrmModule.forRoot({
          type: 'postgres',
          url,
          schema,
          extra: { options: `-c search_path=${schema}` },
          entities: [User, AuthSession, EmailChallenge],
          migrations: [
            AuthSessions1791763200000,
            EmailVerification1791849600000,
            AccountRoles1791896400000,
          ],
          migrationsRun: true,
        }),
        AuthModule,
      ],
    })
      .overrideProvider(EmailService)
      .useValue({ sendCode })
      .compile();
    app = module.createNestApplication();
    configureHttp(app);
    SwaggerModule.setup('api/docs', app, contract);
    await app.init();
    db = module.get(DataSource);
    jwt = module.get(JwtService);
  }, 30000);
  afterAll(async () => {
    if (app) await app.close();
    if (admin?.isInitialized) {
      await admin.query(`DROP SCHEMA IF EXISTS "${schema}" CASCADE`);
      await admin.destroy();
    }
  });
  const validateResponse = (
    path: string,
    verb: 'post' | 'get',
    response: request.Response,
  ) => {
    const operation = contract.paths[path]?.[verb];
    if (!operation) return;
    let definition = operation.responses[String(response.status)] as {
      $ref?: string;
      content?: Record<string, { schema?: { $ref?: string } }>;
    };
    expect(definition).toBeDefined();
    if (definition.$ref)
      definition = contract.components!.responses![
        definition.$ref.split('/').pop()!
      ] as typeof definition;
    const schema = definition.content!['application/json'].schema!.$ref!;
    const validate = ajv.getSchema(`auth-contract${schema}`)!;
    expect(validate(response.body)).toBe(true);
  };
  const post = (path: string, body: object) =>
    request(app.getHttpServer())
      .post(`/auth/${path}`)
      .send(body)
      .expect((response) =>
        validateResponse(`/auth/${path}`, 'post', response),
      );
  const get = (path: string) =>
    request(app.getHttpServer())
      .get(path)
      .expect((response) => validateResponse(path, 'get', response));

  const credentials = {
    username: registration.username,
    password: registration.password,
  };
  const latestCode = (email: string, purpose: string) =>
    [...mail]
      .reverse()
      .find((item) => item.email === email && item.purpose === purpose)!.code;
  const startRegistration = async (body: typeof registration) => {
    const challenge = (await post('register', body).expect(202)).body;
    return {
      challenge_id: challenge.challenge_id,
      code: latestCode(body.email.toLowerCase(), 'REGISTER'),
    };
  };
  const register = async (body: typeof registration) =>
    (await post('register/verify', await startRegistration(body)).expect(201))
      .body;
  const login = async () => {
    // Remove throttle time only in the isolated test DB; separate tests exercise real limits.
    await db.query(
      "UPDATE email_challenges SET created_at = NOW() - INTERVAL '2 hours' WHERE purpose = 'LOGIN' AND email = $1",
      [registration.email],
    );
    const challenge = (await post('login', credentials).expect(202)).body;
    return (
      await post('login/verify', {
        challenge_id: challenge.challenge_id,
        code: latestCode(registration.email, 'LOGIN'),
      }).expect(200)
    ).body as {
      access_token: string;
      refresh_token: string;
      expires_in: number;
    };
  };
  it('requires email verification before registering and assigns the merged UUID account ID format', async () => {
    const verification = await startRegistration(registration);
    expect(
      await db.query('SELECT id FROM accounts WHERE email = $1', [
        registration.email,
      ]),
    ).toHaveLength(0);
    const response = await post('register/verify', verification).expect(201);
    expect(response.body).toMatchObject({
      email: registration.email,
      username: 'newalice',
      role: 'USER',
      account_id: expect.stringMatching(/^ACC-[0-9A-F]{8}$/),
      email_verified: true,
    });
    expect(response.body.password).toBeUndefined();
    const [stored] = await db.query(
      'SELECT password_hash FROM accounts WHERE email = $1',
      [registration.email],
    );
    expect(stored.password_hash).toMatch(/^\$2[aby]\$/);
    expect(stored.password_hash).not.toBe(registration.password);
    await post('register/verify', verification).expect(401);
  });
  it('preserves merged role lists and upgrades old schemas without losing primary roles', async () => {
    const runner = db.createQueryRunner();
    await runner.connect();
    await runner.startTransaction();
    try {
      const migration = new AccountRoles1791896400000();
      const before = await runner.query('SELECT id, roles FROM accounts ORDER BY id');
      await migration.up(runner);
      expect(await runner.query('SELECT id, roles FROM accounts ORDER BY id')).toEqual(before);
      await runner.query("UPDATE accounts SET role = 'ADMIN' WHERE username = 'alice'");
      await runner.query('ALTER TABLE accounts DROP COLUMN roles');
      await migration.up(runner);
      expect(await runner.query("SELECT role, roles FROM accounts WHERE username = 'alice'"))
        .toEqual([{ role: 'ADMIN', roles: 'ADMIN' }]);
      await migration.up(runner);
      expect(await runner.query("SELECT roles FROM accounts WHERE username = 'alice'"))
        .toEqual([{ roles: 'ADMIN' }]);
    } finally {
      await runner.rollbackTransaction();
      await runner.release();
    }
  });
  it('initializes required account fields when the shared table has no defaults', async () => {
    // Hibernate-created tables rely on Java constructors rather than SQL defaults.
    await db.query(`ALTER TABLE accounts
      ALTER COLUMN roles DROP DEFAULT,
      ALTER COLUMN cash_balance DROP DEFAULT,
      ALTER COLUMN version DROP DEFAULT,
      ALTER COLUMN created_on DROP DEFAULT,
      ALTER COLUMN last_updated DROP DEFAULT`);
    try {
      const details = {
        ...registration,
        username: 'nodefaults',
        email: 'nodefaults@example.com',
      };
      const verification = await startRegistration(details);
      const startedAt = Date.now();
      const response = await request(app.getHttpServer())
        .post('/auth/register/verify')
        .send(verification)
        .expect(201);
      validateResponse('/auth/register/verify', 'post', response);
      expect(response.body.account_id).toMatch(/^ACC-[0-9A-F]{8}$/);
      expect(response.body.email_verified).toBe(true);
      const [stored] = await db.query(
        'SELECT roles, cash_balance, version, created_on, last_updated FROM accounts WHERE username = $1',
        [details.username],
      );
      expect(stored.roles).toBe('USER');
      expect(stored.cash_balance).toBe('0.00');
      expect(stored.version).toBe(0);
      expect(stored.created_on.getTime()).toBeGreaterThanOrEqual(startedAt);
      expect(stored.created_on.getTime()).toBeLessThanOrEqual(Date.now());
      expect(stored.last_updated).toEqual(stored.created_on);
      await post('login', {
        username: details.username,
        password: details.password,
      }).expect(202);
    } finally {
      await db.query(`ALTER TABLE accounts
        ALTER COLUMN roles SET DEFAULT 'USER',
        ALTER COLUMN cash_balance SET DEFAULT 0,
        ALTER COLUMN version SET DEFAULT 0,
        ALTER COLUMN created_on SET DEFAULT NOW(),
        ALTER COLUMN last_updated SET DEFAULT NOW()`);
    }
  });
  it('requires unique username and email, including case-insensitive email conflicts', async () => {
    await post('register', {
      ...registration,
      email: registration.email.toUpperCase(),
    }).expect(409);
    await post('register', {
      ...registration,
      email: 'other@example.com',
    }).expect(409);
  });
  it('validates required username/email/password and rejects email-only login', async () => {
    for (const body of [
      { email: 'valid@example.com', password: registration.password },
      { ...registration, email: 'bad' },
      { ...registration, password: 'short' },
      { ...registration, password: '😀'.repeat(30) },
      { ...registration, role: 'ADMIN' },
    ])
      await post('register', body).expect(400);
    for (const body of [
      {},
      { email: registration.email, password: registration.password },
      { username: 'newalice' },
      { ...credentials, email: registration.email },
      { ...credentials, password: 123 },
    ])
      await post('login', body).expect(400);
    await post('login/verify', { challenge_id: 'bad', code: '123456' }).expect(
      400,
    );
  });
  it('correct password alone returns only a challenge; second factor issues guard-compatible tokens', async () => {
    const active = await login();
    expect(active.expires_in).toBe(120);
    expect(active.refresh_token).toMatch(/^[a-f0-9]{64}$/);
    const payload = jwt.verify(active.access_token);
    expect(payload.amr).toEqual(['pwd', 'email_otp']);
    expect(payload.exp - payload.iat).toBe(120);
    expect(
      (
        await get('/auth/validate')
          .auth(active.access_token, { type: 'bearer' })
          .expect(200)
      ).body,
    ).toEqual({ valid: true, sub: payload.sub, role: 'USER' });
    expect(
      (
        await get('/auth/me')
          .auth(active.access_token, { type: 'bearer' })
          .expect(200)
      ).body.account_id,
    ).toMatch(/^ACC-[0-9A-F]{8}$/);
    const [session] = await db.query(
      'SELECT token_hash FROM auth_sessions WHERE id = $1',
      [payload.sid],
    );
    expect(session.token_hash).not.toBe(active.refresh_token);
  });
  it('returns identical generic errors for wrong password and unknown username', async () => {
    const wrong = await post('login', {
      ...credentials,
      password: 'wrong',
    }).expect(401);
    const unknown = await post('login', {
      ...credentials,
      username: 'unknown',
    }).expect(401);
    expect(wrong.body).toEqual(unknown.body);
    expect(wrong.body.message).toBe('Invalid credentials');
  });
  it('rejects wrong, expired, purpose-mismatched and reused email codes', async () => {
    const body = {
      ...registration,
      username: 'code-test',
      email: 'code-test@example.com',
    };
    const verification = await startRegistration(body);
    await post('register/verify', { ...verification, code: '000000' }).expect(
      401,
    );
    await post('login/verify', verification).expect(401);
    const [stored] = await db.query(
      'SELECT code_hash, payload, attempts FROM email_challenges WHERE id = $1',
      [verification.challenge_id],
    );
    expect(stored.code_hash).not.toBe(verification.code);
    expect(stored.attempts).toBe(1);
    expect(JSON.stringify(stored.payload)).not.toContain(registration.password);
    await db.query(
      "UPDATE email_challenges SET expires_at = NOW() - INTERVAL '1 second' WHERE id = $1",
      [verification.challenge_id],
    );
    await post('register/verify', verification).expect(401);
  });
  it('persists failed attempts and locks the challenge after five wrong codes', async () => {
    const verification = await startRegistration({
      ...registration,
      username: 'attempt-test',
      email: 'attempt-test@example.com',
    });
    for (let i = 0; i < 5; i++)
      await post('register/verify', { ...verification, code: '000000' }).expect(
        401,
      );
    await post('register/verify', verification).expect(401);
    expect(
      (
        await db.query('SELECT attempts FROM email_challenges WHERE id = $1', [
          verification.challenge_id,
        ])
      )[0].attempts,
    ).toBe(5);
  });
  it('limits code delivery to one per minute and five per hour', async () => {
    const body = {
      ...registration,
      username: 'limit-test',
      email: 'limit-test@example.com',
    };
    await post('register', body).expect(202);
    await post('register', body).expect(429);
    for (let i = 1; i < 5; i++) {
      await db.query(
        "UPDATE email_challenges SET created_at = created_at - INTERVAL '61 seconds' WHERE email = $1",
        [body.email],
      );
      await post('register', body).expect(202);
    }
    await db.query(
      "UPDATE email_challenges SET created_at = created_at - INTERVAL '61 seconds' WHERE email = $1",
      [body.email],
    );
    await post('register', body).expect(429);
  });
  it('does not activate accounts or return tokens if email delivery fails', async () => {
    sendCode.mockRejectedValueOnce(
      new ServiceUnavailableException('Email verification is unavailable'),
    );
    await post('register', {
      ...registration,
      username: 'mail-failure',
      email: 'mail-failure@example.com',
    }).expect(503);
    expect(
      await db.query('SELECT id FROM accounts WHERE email = $1', [
        'mail-failure@example.com',
      ]),
    ).toHaveLength(0);
    expect(
      await db.query('SELECT id FROM email_challenges WHERE email = $1', [
        'mail-failure@example.com',
      ]),
    ).toHaveLength(0);
  });
  it('cannot issue a login session when SMTP delivery fails', async () => {
    await db.query(
      "UPDATE email_challenges SET created_at = NOW() - INTERVAL '2 hours' WHERE purpose = 'LOGIN'",
    );
    const before = (
      await db.query('SELECT COUNT(*) AS count FROM auth_sessions')
    )[0].count;
    sendCode.mockRejectedValueOnce(
      new ServiceUnavailableException('Email verification is unavailable'),
    );
    await post('login', credentials).expect(503);
    expect(
      (await db.query('SELECT COUNT(*) AS count FROM auth_sessions'))[0].count,
    ).toBe(before);
  });
  it('allows only one concurrent verification to create a login session', async () => {
    await db.query(
      "UPDATE email_challenges SET created_at = NOW() - INTERVAL '2 hours' WHERE purpose = 'LOGIN'",
    );
    const challenge = (await post('login', credentials).expect(202)).body;
    expect(challenge.access_token).toBeUndefined();
    expect(challenge.refresh_token).toBeUndefined();
    const verification = {
      challenge_id: challenge.challenge_id,
      code: latestCode(registration.email, 'LOGIN'),
    };
    const responses = await Promise.all([
      post('login/verify', verification),
      post('login/verify', verification),
    ]);
    expect(responses.map((item) => item.status).sort((a, b) => a - b)).toEqual([
      200, 401,
    ]);
  });
  it('assigns unique UUID-based account IDs during concurrent verified registrations', async () => {
    const first = await startRegistration({
      ...registration,
      username: 'concurrent-one',
      email: 'concurrent-one@example.com',
    });
    const second = await startRegistration({
      ...registration,
      username: 'concurrent-two',
      email: 'concurrent-two@example.com',
    });
    const responses = await Promise.all([
      post('register/verify', first).expect(201),
      post('register/verify', second).expect(201),
    ]);
    for (const response of responses)
      expect(response.body.account_id).toMatch(/^ACC-[0-9A-F]{8}$/);
    expect(responses[0].body.account_id).not.toBe(responses[1].body.account_id);
  });
  it('failed duplicate verification does not create another account', async () => {
    const first = await startRegistration({
      ...registration,
      username: 'reserved-name',
      email: 'reserved-one@example.com',
    });
    const second = await startRegistration({
      ...registration,
      username: 'reserved-name',
      email: 'reserved-two@example.com',
    });
    const created = (await post('register/verify', first).expect(201)).body;
    await post('register/verify', second).expect(409);
    const rows = await db.query(
      'SELECT account_id FROM accounts WHERE username = $1',
      ['reserved-name'],
    );
    expect(rows).toEqual([{ account_id: created.account_id }]);
  });
  it('new registrations use the UUID format after another account is deleted', async () => {
    const first = await register({
      ...registration,
      username: 'deleted-account',
      email: 'deleted-account@example.com',
    });
    await db.query('DELETE FROM accounts WHERE id = $1', [first.id]);
    const next = await register({
      ...registration,
      username: 'after-deletion',
      email: 'after-deletion@example.com',
    });
    expect(next.account_id).toMatch(/^ACC-[0-9A-F]{8}$/);
    expect(next.account_id).not.toBe(first.account_id);
  });
  it('refresh rotates tokens, rejects reuse and allows only one concurrent consumer', async () => {
    const first = await login();
    const rotated = await post('refresh', {
      refresh_token: first.refresh_token,
    }).expect(200);
    expect(rotated.body.refresh_token).not.toBe(first.refresh_token);
    await post('refresh', { refresh_token: first.refresh_token }).expect(401);
    await post('refresh', { refresh_token: 'invalid-token' }).expect(401);
    const responses = await Promise.all([
      post('refresh', { refresh_token: rotated.body.refresh_token }),
      post('refresh', { refresh_token: rotated.body.refresh_token }),
    ]);
    expect(responses.map((item) => item.status).sort((a, b) => a - b)).toEqual([
      200, 401,
    ]);
  });
  it('rejects expired refresh and access tokens and tokens without MFA claims', async () => {
    const active = await login();
    const payload = jwt.verify(active.access_token);
    await db.query(
      "UPDATE auth_sessions SET expires_at = NOW() - INTERVAL '1 second' WHERE id = $1",
      [payload.sid],
    );
    await post('refresh', { refresh_token: active.refresh_token }).expect(401);
    const fresh = await login();
    const freshClaims = jwt.verify(fresh.access_token);
    const claims = {
      sub: freshClaims.sub,
      sid: freshClaims.sid,
      role: freshClaims.role,
      amr: freshClaims.amr,
    };
    for (const access of [
      jwt.sign(claims, { expiresIn: -1 }),
      jwt.sign({ ...claims, amr: ['pwd'] }, { expiresIn: 120 }),
      jwt.sign(claims, { secret: 'wrong', expiresIn: 120 }),
    ])
      await get('/auth/me').auth(access, { type: 'bearer' }).expect(401);
    await get('/auth/me').expect(401);
  });
  it('logout revokes refresh and NestJS access validation', async () => {
    const active = await login();
    await post('logout', { refresh_token: active.refresh_token }).expect(200);
    await post('refresh', { refresh_token: active.refresh_token }).expect(401);
    await get('/auth/me')
      .auth(active.access_token, { type: 'bearer' })
      .expect(401);
    await post('logout', {}).expect(400);
  });
  it('suspended accounts receive 403 on the profile route and cannot refresh', async () => {
    const active = await login();
    const claims = jwt.verify(active.access_token);
    await db.query("UPDATE accounts SET status = 'SUSPENDED' WHERE id = $1", [
      claims.sub,
    ]);
    await get('/auth/me')
      .auth(active.access_token, { type: 'bearer' })
      .expect(403);
    await post('refresh', { refresh_token: active.refresh_token }).expect(401);
    await db.query("UPDATE accounts SET status = 'ACTIVE' WHERE id = $1", [
      claims.sub,
    ]);
  });
  it('serves the canonical contract and removes arbitrary token signing', async () => {
    await request(app.getHttpServer()).get('/api/docs/').expect(200);
    const document = await request(app.getHttpServer())
      .get('/api/docs-json')
      .expect(200);
    expect(document.body.paths).toEqual(contract.paths);
    await post('token', { sub: 'admin', roles: ['ADMIN'] }).expect(404);
  });
});
