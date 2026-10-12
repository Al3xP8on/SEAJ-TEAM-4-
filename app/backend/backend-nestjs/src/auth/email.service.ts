import { Injectable, ServiceUnavailableException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { createTransport } from 'nodemailer';
@Injectable()
export class EmailService {
  constructor(private readonly config: ConfigService) {}
  async sendCode(
    email: string,
    code: string,
    purpose: 'REGISTER' | 'LOGIN',
    expirySeconds: number,
  ): Promise<void> {
    const host = this.config.get<string>('SMTP_HOST');
    const from = this.config.get<string>('SMTP_FROM');
    const user = this.config.get<string>('SMTP_USER');
    const pass = this.config.get<string>('SMTP_PASSWORD');
    const port = Number(this.config.get<string>('SMTP_PORT') || '587');
    const secureValue = this.config.get<string>('SMTP_SECURE') || 'false';
    if (
      !host ||
      !from ||
      !user ||
      !pass ||
      !Number.isInteger(port) ||
      port < 1 ||
      port > 65535 ||
      !['true', 'false'].includes(secureValue)
    ) {
      throw new ServiceUnavailableException(
        'Email verification is unavailable',
      );
    }
    const secure = secureValue === 'true';
    const transport = createTransport({
      host,
      port,
      secure,
      requireTLS: !secure,
      auth: { user, pass },
      connectionTimeout: 10000,
      greetingTimeout: 10000,
      socketTimeout: 10000,
    });
    try {
      const result = await transport.sendMail({
        from,
        to: email,
        subject:
          purpose === 'REGISTER'
            ? 'Verify your SEAJ registration'
            : 'Your SEAJ login code',
        text: `Your verification code is ${code}. It expires in ${expirySeconds} seconds. If you did not request this, ignore this email.`,
      });
      if (!result.accepted.length) throw new Error('Recipient not accepted');
    } catch {
      throw new ServiceUnavailableException(
        'Email verification is unavailable',
      );
    } finally {
      transport.close();
    }
  }
}
