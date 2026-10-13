// Tests SMTP configuration, secure transport and verification-email delivery handling.
// Uses mocked mail transport to check success and failure without sending real emails.
import { ConfigService } from '@nestjs/config';
import { createTransport } from 'nodemailer';
import { EmailService } from './email.service';
jest.mock('nodemailer', () => ({ createTransport: jest.fn() }));
describe('SMTP EmailService', () => {
  const settings = {
    SMTP_HOST: 'smtp.example.com',
    SMTP_PORT: '587',
    SMTP_USER: 'test-user',
    SMTP_PASSWORD: 'test-password',
    SMTP_FROM: 'seaj@example.com',
  };
  const transportFactory = jest.mocked(createTransport);
  const sendMail = jest.fn();
  const close = jest.fn();
  beforeEach(() => {
    jest.clearAllMocks();
    sendMail.mockResolvedValue({ accepted: ['alice@example.com'] });
    transportFactory.mockReturnValue({
      sendMail,
      close,
    } as unknown as ReturnType<typeof createTransport>);
  });
  function service(values: Record<string, string>) {
    const config = new ConfigService(values);
    jest.spyOn(config, 'get').mockImplementation((key: string) => values[key]);
    return new EmailService(config);
  }
  it('sends the code to the configured recipient over mandatory STARTTLS', async () => {
    await service(settings).sendCode(
      'alice@example.com',
      '123456',
      'LOGIN',
      300,
    );
    expect(transportFactory).toHaveBeenCalledWith(
      expect.objectContaining({
        secure: false,
        requireTLS: true,
        auth: { user: 'test-user', pass: 'test-password' },
      }),
    );
    expect(sendMail).toHaveBeenCalledWith(
      expect.objectContaining({
        to: 'alice@example.com',
        text: expect.stringContaining('123456'),
      }),
    );
    expect(close).toHaveBeenCalled();
  });
  it('supports direct TLS SMTP', async () => {
    await service({
      ...settings,
      SMTP_SECURE: 'true',
      SMTP_PORT: '465',
    }).sendCode('alice@example.com', '123456', 'REGISTER', 300);
    expect(transportFactory).toHaveBeenCalledWith(
      expect.objectContaining({ secure: true }),
    );
  });
  it('fails closed without SMTP credentials', async () => {
    await expect(
      service({}).sendCode('alice@example.com', '123456', 'LOGIN', 300),
    ).rejects.toThrow('Email verification is unavailable');
    expect(sendMail).not.toHaveBeenCalled();
  });
  it('returns a generic unavailable error on mail delivery failure', async () => {
    sendMail.mockRejectedValue(new Error('sensitive SMTP details'));
    await expect(
      service(settings).sendCode('alice@example.com', '123456', 'LOGIN', 300),
    ).rejects.toThrow('Email verification is unavailable');
    expect(close).toHaveBeenCalled();
  });
});
