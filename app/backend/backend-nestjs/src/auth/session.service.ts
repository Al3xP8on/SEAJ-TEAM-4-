import { Injectable, UnauthorizedException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { MoreThan, Repository } from 'typeorm';
import type { EntityManager } from 'typeorm';
import { createHash, randomBytes, randomUUID } from 'node:crypto';
import { AuthSession } from './auth-session.entity';
import { AuthSettings } from './auth.settings';

@Injectable()
export class SessionService {
  constructor(
    @InjectRepository(AuthSession)
    private readonly sessions: Repository<AuthSession>,
    private readonly settings: AuthSettings,
  ) {}
  private digest(token: string): string {
    return createHash('sha256').update(token).digest('hex');
  }
  async create(
    userId: string,
    manager?: EntityManager,
  ): Promise<{ session: AuthSession; refreshToken: string }> {
    const refreshToken = randomBytes(32).toString('hex');
    const repository = manager?.getRepository(AuthSession) ?? this.sessions;
    const session = await repository.save(
      repository.create({
        id: randomUUID(),
        userId,
        tokenHash: this.digest(refreshToken),
        expiresAt: new Date(Date.now() + this.settings.refreshSeconds * 1000),
        revoked: false,
      }),
    );
    return { session, refreshToken };
  }
  async rotate(
    token: string,
  ): Promise<{ session: AuthSession; refreshToken: string }> {
    const refreshToken = randomBytes(32).toString('hex');
    // A conditional UPDATE atomically consumes the old token, including across replicas.
    const result = await this.sessions
      .createQueryBuilder()
      .update(AuthSession)
      .set({ tokenHash: this.digest(refreshToken) })
      .where('token_hash = :hash AND revoked = false AND expires_at > :now', {
        hash: this.digest(token),
        now: new Date(),
      })
      .returning(['id', 'userId', 'expiresAt'])
      .execute();
    const row = (
      result.raw as { id: string; user_id: string; expires_at: Date }[]
    )[0];
    if (!row)
      throw new UnauthorizedException('Invalid or expired refresh token');
    return {
      session: Object.assign(new AuthSession(), {
        id: row.id,
        userId: String(row.user_id),
        expiresAt: row.expires_at,
        revoked: false,
      }),
      refreshToken,
    };
  }
  async revoke(token: string): Promise<void> {
    const result = await this.sessions
      .createQueryBuilder()
      .update(AuthSession)
      .set({ revoked: true })
      .where('token_hash = :hash AND revoked = false AND expires_at > :now', {
        hash: this.digest(token),
        now: new Date(),
      })
      .execute();
    if (!result.affected)
      throw new UnauthorizedException('Invalid or expired refresh token');
  }
  async revokeSession(id: string): Promise<void> {
    await this.sessions.update({ id }, { revoked: true });
  }
  async isActive(id: string, userId: string): Promise<boolean> {
    return await this.sessions.existsBy({
      id,
      userId,
      revoked: false,
      expiresAt: MoreThan(new Date()),
    });
  }
}
