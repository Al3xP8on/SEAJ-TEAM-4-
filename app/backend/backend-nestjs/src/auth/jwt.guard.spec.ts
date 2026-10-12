import type { ExecutionContext } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { JwtAuthGuard } from './jwt.guard';
import { SessionService } from './session.service';
describe('JwtAuthGuard', () => {
  const jwt = new JwtService({ secret: 'test-secret' });
  const active = jest.fn();
  const guard = new JwtAuthGuard(jwt, {
    isActive: active,
  } as unknown as SessionService);
  const context = (authorization?: string) =>
    ({
      switchToHttp: () => ({
        getRequest: () => ({ headers: { authorization } }),
      }),
    }) as ExecutionContext;
  const payload = {
    sub: '42',
    role: 'USER',
    sid: 'session-id',
    amr: ['pwd', 'email_otp'],
  };
  beforeEach(() => active.mockResolvedValue(true));
  it('accepts an issued token with active session', async () => {
    expect(
      await guard.canActivate(
        context(`Bearer ${jwt.sign(payload, { expiresIn: 120 })}`),
      ),
    ).toBe(true);
    expect(active).toHaveBeenCalledWith('session-id', '42');
  });
  it('rejects expired tokens', async () => {
    await expect(
      guard.canActivate(
        context(`Bearer ${jwt.sign(payload, { expiresIn: -1 })}`),
      ),
    ).rejects.toThrow('Invalid or expired token');
  });
  it('rejects revoked sessions', async () => {
    active.mockResolvedValue(false);
    await expect(
      guard.canActivate(
        context(`Bearer ${jwt.sign(payload, { expiresIn: 120 })}`),
      ),
    ).rejects.toThrow('Invalid or expired token');
  });
  it('rejects wrong signatures and tokens missing exp', async () => {
    await expect(
      guard.canActivate(
        context(
          `Bearer ${jwt.sign(payload, { secret: 'different', expiresIn: 120 })}`,
        ),
      ),
    ).rejects.toThrow();
    await expect(
      guard.canActivate(context(`Bearer ${jwt.sign(payload)}`)),
    ).rejects.toThrow();
  });
  it('rejects a token issued without an email second factor', async () => {
    await expect(
      guard.canActivate(
        context(
          `Bearer ${jwt.sign({ ...payload, amr: ['pwd'] }, { expiresIn: 120 })}`,
        ),
      ),
    ).rejects.toThrow();
  });
  it('rejects missing and malformed authorization', async () => {
    for (const header of [undefined, 'Basic abc', 'Bearer', 'Bearer a b'])
      await expect(guard.canActivate(context(header))).rejects.toThrow();
  });
});
