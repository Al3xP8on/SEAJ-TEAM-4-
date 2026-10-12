import { ConfigService } from '@nestjs/config';
import { AuthSettings } from './auth.settings';
function settings(values: Record<string, string>) {
  const config = new ConfigService(values);
  config.skipProcessEnv = true;
  return new AuthSettings(config);
}
describe('AuthSettings', () => {
  it('defaults to 30 minutes and one week', () => {
    const value = settings({
      JWT_SECRET: 'test-only-secret-with-at-least-32-bytes',
    });
    expect(value.accessSeconds).toBe(1800);
    expect(value.refreshSeconds).toBe(604800);
  });
  it('uses configured integer seconds', () => {
    expect(
      settings({
        JWT_SECRET: 'test-only-secret-with-at-least-32-bytes',
        JWT_EXPIRY: '90',
        JWT_REFRESH_EXPIRY: '600',
      }).accessSeconds,
    ).toBe(90);
  });
  it.each(['', 'changeme', 'JWT_SECRET', 'too-short'])(
    'rejects missing or placeholder secret %s',
    (secret) => {
      expect(() => settings({ JWT_SECRET: secret })).toThrow('JWT_SECRET');
    },
  );
  it.each(['-1', '0', '1h', '1.5', 'NaN'])(
    'rejects invalid expiry %s',
    (expiry) => {
      expect(() =>
        settings({
          JWT_SECRET: 'test-only-secret-with-at-least-32-bytes',
          JWT_EXPIRY: expiry,
        }),
      ).toThrow('expiry');
    },
  );
});
