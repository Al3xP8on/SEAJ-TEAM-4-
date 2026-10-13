import {
  HttpException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import {
  createHmac,
  randomInt,
  randomUUID,
  timingSafeEqual,
} from 'node:crypto';
import { Repository } from 'typeorm';
import type { EntityManager } from 'typeorm';
import { EmailChallenge } from './email-challenge.entity';
import { EmailService } from './email.service';
import { AuthSettings } from './auth.settings';
import { VerifyDto } from './dto/verify.dto';
@Injectable()
export class EmailChallengeService {
  constructor(
    @InjectRepository(EmailChallenge)
    private readonly challenges: Repository<EmailChallenge>,
    private readonly email: EmailService,
    private readonly settings: AuthSettings,
  ) {}
  private digest(id: string, code: string): string {
    return createHmac('sha256', this.settings.secret)
      .update(`email-code:${id}:${code}`)
      .digest('hex');
  }
  async issue(
    purpose: EmailChallenge['purpose'],
    email: string,
    payload: NonNullable<EmailChallenge['payload']>,
  ) {
    const id = randomUUID();
    const code = String(randomInt(100000, 1000000));
    const challenge = await this.challenges.manager.transaction(
      async (manager) => {
        // Serialize issuance per recipient/purpose so throttling also works across replicas.
        await manager.query('SELECT pg_advisory_xact_lock(hashtext($1))', [
          `${purpose}:${email}`,
        ]);
        const repository = manager.getRepository(EmailChallenge);
        const recent = await repository
          .createQueryBuilder('challenge')
          .where(
            'challenge.email = :email AND challenge.purpose = :purpose AND challenge.created_at > :since',
            { email, purpose, since: new Date(Date.now() - 3600000) },
          )
          .orderBy('challenge.created_at', 'DESC')
          .getMany();
        if (
          recent.length >= 5 ||
          (recent[0] && recent[0].createdAt.getTime() > Date.now() - 60000)
        ) {
          throw new HttpException(
            'Please wait before requesting another verification code',
            429,
          );
        }
        return repository.save(
          repository.create({
            id,
            purpose,
            email,
            payload,
            codeHash: this.digest(id, code),
            createdAt: new Date(),
            expiresAt: new Date(
              Date.now() + this.settings.emailCodeSeconds * 1000,
            ),
            attempts: 0,
            consumedAt: null,
          }),
        );
      },
    );
    try {
      await this.email.sendCode(
        email,
        code,
        purpose,
        this.settings.emailCodeSeconds,
      );
    } catch (error) {
      await this.challenges.delete({ id });
      throw error;
    }
    return {
      challenge_id: challenge.id,
      expires_in: this.settings.emailCodeSeconds,
      message: 'Enter the code sent to your email',
    };
  }
  async verify<T>(
    dto: VerifyDto,
    purpose: EmailChallenge['purpose'],
    complete: (challenge: EmailChallenge, manager: EntityManager) => Promise<T>,
  ): Promise<T> {
    const result = await this.challenges.manager.transaction(
      async (manager) => {
        const repository = manager.getRepository(EmailChallenge);
        const challenge = await repository
          .createQueryBuilder('challenge')
          .addSelect(['challenge.codeHash', 'challenge.payload'])
          .where('challenge.id = :id AND challenge.purpose = :purpose', {
            id: dto.challenge_id,
            purpose,
          })
          .setLock('pessimistic_write')
          .getOne();
        if (
          !challenge ||
          challenge.consumedAt ||
          challenge.expiresAt.getTime() <= Date.now() ||
          challenge.attempts >= 5
        )
          return { valid: false as const };
        const expected = Buffer.from(challenge.codeHash, 'hex');
        const actual = Buffer.from(this.digest(challenge.id, dto.code), 'hex');
        if (!timingSafeEqual(expected, actual)) {
          await repository.update(
            { id: challenge.id },
            { attempts: challenge.attempts + 1 },
          );
          return { valid: false as const };
        }
        const value = await complete(challenge, manager);
        await repository.update(
          { id: challenge.id },
          { consumedAt: new Date(), payload: null },
        );
        return { valid: true as const, value };
      },
    );
    // Throw after commit so wrong-code attempt increments cannot be rolled back.
    if (!result.valid)
      throw new UnauthorizedException('Invalid or expired verification code');
    return result.value;
  }
}
