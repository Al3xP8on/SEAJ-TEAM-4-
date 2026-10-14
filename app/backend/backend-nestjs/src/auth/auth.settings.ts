import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

@Injectable()
export class AuthSettings {
  readonly secret: string;
  readonly accessSeconds: number;
  readonly refreshSeconds: number;
  readonly emailCodeSeconds: number;
  constructor(config: ConfigService) {
    const secret = config.get<string>('JWT_SECRET');
    if (
      !secret?.trim() ||
      ['changeme', 'JWT_SECRET'].includes(secret) ||
      Buffer.byteLength(secret, 'utf8') < 32
    ) {
      throw new Error(
        'JWT_SECRET must be configured with a non-placeholder secret of at least 32 bytes',
      );
    }
    this.secret = secret;
    this.emailCodeSeconds = this.seconds(
      config.get<string>('EMAIL_CODE_EXPIRY'),
      300,
    );
    this.accessSeconds = this.seconds(config.get<string>('JWT_EXPIRY'), 1800);
    this.refreshSeconds = this.seconds(
      config.get<string>('JWT_REFRESH_EXPIRY'),
      604800,
    );
  }
  private seconds(value: string | undefined, fallback: number): number {
    const parsed =
      value === undefined || value === '' ? fallback : Number(value);
    if (!Number.isSafeInteger(parsed) || parsed <= 0)
      throw new Error('Token expiry must be a positive integer in seconds');
    return parsed;
  }
}
