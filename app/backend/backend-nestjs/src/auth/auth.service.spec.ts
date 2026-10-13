// Tests authentication orchestration using mocked users, sessions and email verification.
// Checks token issuance, credential failures and refresh rules with isolated configuration values.
import { UnauthorizedException } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { ConfigService } from '@nestjs/config';
import { createHash } from 'node:crypto';
import type { EntityManager } from 'typeorm';
import { AuthService } from './auth.service';
import { AuthSettings } from './auth.settings';
import { UsersService } from '../users/users.service';
import { PasswordService } from './password.service';
import { SessionService } from './session.service';
import { EmailChallengeService } from './email-challenge.service';
import { EmailChallenge } from './email-challenge.entity';
describe('AuthService: username/password plus email code', () => {
  const user = {
    id: '42',
    accountId: 'ACC-7A3F91C2',
    email: 'alice@example.com',
    username: 'alice',
    name: 'Alice',
    password: 'stored-hash',
    role: 'USER',
    status: 'ACTIVE',
    emailVerified: true,
  };
  const dto = {
    challenge_id: '00000000-0000-4000-8000-000000000001',
    code: '123456',
  };
  let users: {
    findByUsername: jest.Mock;
    findById: jest.Mock;
    findCredentialsById: jest.Mock;
    prepareRegistration: jest.Mock;
    registerVerified: jest.Mock;
  };
  let passwords: { verify: jest.Mock };
  let sessions: {
    create: jest.Mock;
    rotate: jest.Mock;
    revoke: jest.Mock;
    revokeSession: jest.Mock;
  };
  let challenges: { issue: jest.Mock; verify: jest.Mock };
  let service: AuthService;
  let jwt: JwtService;
  const update = jest.fn();
  const manager = {
    getRepository: () => ({ update }),
  } as unknown as EntityManager;
  beforeEach(() => {
    const values: Record<string, string> = {
      JWT_SECRET: 'test-only-secret-with-at-least-32-bytes',
      JWT_EXPIRY: '120',
    };
    const config = new ConfigService(values);
    jest.spyOn(config, 'get').mockImplementation((key: string) => values[key]);
    const settings = new AuthSettings(config);
    jwt = new JwtService({
      secret: settings.secret,
      signOptions: { algorithm: 'HS256' },
    });
    users = {
      findByUsername: jest.fn().mockResolvedValue(user),
      findById: jest.fn().mockResolvedValue(user),
      findCredentialsById: jest.fn().mockResolvedValue(user),
      prepareRegistration: jest
        .fn()
        .mockResolvedValue({
          email: user.email,
          username: user.username,
          name: user.name,
          passwordHash: user.password,
        }),
      registerVerified: jest.fn().mockResolvedValue(user),
    };
    passwords = { verify: jest.fn().mockResolvedValue(true) };
    sessions = {
      create: jest
        .fn()
        .mockResolvedValue({
          session: { id: 'session-id' },
          refreshToken: 'refresh',
        }),
      rotate: jest.fn(),
      revoke: jest.fn(),
      revokeSession: jest.fn(),
    };
    challenges = {
      issue: jest
        .fn()
        .mockResolvedValue({
          challenge_id: dto.challenge_id,
          expires_in: 300,
          message: 'Enter code',
        }),
      verify: jest.fn(),
    };
    challenges.verify.mockImplementation(
      async (
        _dto: unknown,
        purpose: string,
        complete: (
          challenge: EmailChallenge,
          manager: EntityManager,
        ) => Promise<unknown>,
      ) => {
        const payload =
          purpose === 'REGISTER'
            ? {
                email: user.email,
                username: user.username,
                name: user.name,
                passwordHash: user.password,
              }
            : {
                userId: user.id,
                email: user.email,
                passwordFingerprint: createHash('sha256')
                  .update(user.password)
                  .digest('hex'),
              };
        return complete(
          Object.assign(new EmailChallenge(), { payload }),
          manager,
        );
      },
    );
    service = new AuthService(
      jwt,
      users as unknown as UsersService,
      passwords as unknown as PasswordService,
      sessions as unknown as SessionService,
      settings,
      challenges as unknown as EmailChallengeService,
    );
  });
  it('username/password starts a challenge and never creates a session or token', async () => {
    const result = await service.login({
      username: 'alice',
      password: 'submitted-password',
    });
    expect(users.findByUsername).toHaveBeenCalledWith('alice');
    expect(passwords.verify).toHaveBeenCalledWith(
      'submitted-password',
      'stored-hash',
    );
    expect(challenges.issue).toHaveBeenCalledWith(
      'LOGIN',
      user.email,
      expect.objectContaining({ userId: '42' }),
    );
    expect(result).toHaveProperty('challenge_id');
    expect(result).not.toHaveProperty('access_token');
    expect(sessions.create).not.toHaveBeenCalled();
  });
  it('only verified second factor issues tokens with identity, role and MFA claims', async () => {
    const result = await service.verifyLogin(dto);
    const payload = jwt.verify(result.access_token);
    expect(payload).toMatchObject({
      sub: '42',
      role: 'USER',
      roles: ['USER'],
      username: 'alice',
      sid: 'session-id',
      amr: ['pwd', 'email_otp'],
    });
    expect(payload.exp - payload.iat).toBe(120);
    expect(payload.password).toBeUndefined();
    expect(JSON.stringify(result)).not.toContain('stored-hash');
    expect(result).toMatchObject({
      expires_in: 120,
      refresh_token: 'refresh',
      token_type: 'Bearer',
    });
    expect(sessions.create).toHaveBeenCalledWith('42', manager);
  });
  it.each([
    'unknown user',
    'wrong password',
    'suspended user',
    'missing email',
  ])('returns generic error for %s', async (reason) => {
    if (reason === 'unknown user') users.findByUsername.mockResolvedValue(null);
    if (reason === 'wrong password') passwords.verify.mockResolvedValue(false);
    if (reason === 'suspended user')
      users.findByUsername.mockResolvedValue({ ...user, status: 'SUSPENDED' });
    if (reason === 'missing email')
      users.findByUsername.mockResolvedValue({ ...user, email: null });
    await expect(
      service.login({ username: 'alice', password: 'password' }),
    ).rejects.toThrow(new UnauthorizedException('Invalid credentials'));
    expect(challenges.issue).not.toHaveBeenCalled();
    expect(sessions.create).not.toHaveBeenCalled();
  });
  it('registration sends a challenge and creates no account until verification', async () => {
    await service.register({
      email: user.email,
      username: 'alice',
      password: 'password',
    });
    expect(challenges.issue).toHaveBeenCalledWith(
      'REGISTER',
      user.email,
      expect.objectContaining({ passwordHash: 'stored-hash' }),
    );
    expect(users.registerVerified).not.toHaveBeenCalled();
    const result = await service.verifyRegistration(dto);
    expect(result).toMatchObject({
      account_id: 'ACC-7A3F91C2',
      username: 'alice',
      email_verified: true,
    });
    expect(result).not.toHaveProperty('password');
  });
  it('rejects a pending login if password changed before second-factor verification', async () => {
    users.findCredentialsById.mockResolvedValue({
      ...user,
      password: 'changed-hash',
    });
    await expect(service.verifyLogin(dto)).rejects.toThrow(
      'Invalid or expired verification code',
    );
    expect(sessions.create).not.toHaveBeenCalled();
  });
  it('refresh refuses an unverified email and revokes the session', async () => {
    sessions.rotate.mockResolvedValue({
      session: { id: 'session-id', userId: '42' },
      refreshToken: 'new-refresh',
    });
    users.findById.mockResolvedValue({ ...user, emailVerified: false });
    await expect(service.refresh('old-refresh')).rejects.toThrow(
      'Invalid or expired refresh token',
    );
    expect(sessions.revokeSession).toHaveBeenCalledWith('session-id');
  });
});
