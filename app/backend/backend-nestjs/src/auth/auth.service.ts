import {
  ForbiddenException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { createHash } from 'node:crypto';
import { UsersService } from '../users/users.service';
import { User } from '../users/user.entity';
import { PasswordService } from './password.service';
import { SessionService } from './session.service';
import { AuthSettings } from './auth.settings';
import { EmailChallengeService } from './email-challenge.service';
import type {
  RegistrationPayload,
  LoginPayload,
} from './email-challenge.entity';
import { LoginDto } from './dto/login.dto';
import { RegisterDto } from './dto/register.dto';
import { VerifyDto } from './dto/verify.dto';
import { AuthResponseDto } from './dto/auth.dto';
@Injectable()
export class AuthService {
  constructor(
    private readonly jwt: JwtService,
    private readonly users: UsersService,
    private readonly passwords: PasswordService,
    private readonly sessions: SessionService,
    private readonly settings: AuthSettings,
    private readonly challenges: EmailChallengeService,
  ) {}
  async register(dto: RegisterDto) {
    const payload = await this.users.prepareRegistration(dto);
    return this.challenges.issue('REGISTER', payload.email, payload);
  }
  verifyRegistration(dto: VerifyDto) {
    return this.challenges.verify(dto, 'REGISTER', async (challenge, manager) =>
      this.publicUser(
        await this.users.registerVerified(
          challenge.payload as RegistrationPayload,
          manager,
        ),
      ),
    );
  }
  async login(dto: LoginDto) {
    const user = await this.users.findByUsername(dto.username);
    if (
      !user ||
      !(await this.passwords.verify(dto.password, user.password)) ||
      user.status !== 'ACTIVE' ||
      !user.email
    ) {
      throw new UnauthorizedException('Invalid credentials');
    }
    return this.challenges.issue('LOGIN', user.email, {
      userId: String(user.id),
      email: user.email,
      passwordFingerprint: this.fingerprint(user.password),
    });
  }
  verifyLogin(dto: VerifyDto): Promise<AuthResponseDto> {
    return this.challenges.verify(dto, 'LOGIN', async (challenge, manager) => {
      const payload = challenge.payload as LoginPayload;
      const user = await this.users.findCredentialsById(
        payload.userId,
        manager,
      );
      if (
        !user ||
        user.status !== 'ACTIVE' ||
        user.email !== payload.email ||
        this.fingerprint(user.password) !== payload.passwordFingerprint
      ) {
        throw new UnauthorizedException('Invalid or expired verification code');
      }
      await manager
        .getRepository(User)
        .update({ id: user.id }, { emailVerified: true });
      const { session, refreshToken } = await this.sessions.create(
        String(user.id),
        manager,
      );
      return this.tokens(user, session.id, refreshToken);
    });
  }
  async refresh(token: string): Promise<AuthResponseDto> {
    const { session, refreshToken } = await this.sessions.rotate(token);
    const user = await this.users.findById(session.userId);
    if (!user || user.status !== 'ACTIVE' || !user.emailVerified) {
      await this.sessions.revokeSession(session.id);
      throw new UnauthorizedException('Invalid or expired refresh token');
    }
    return this.tokens(user, session.id, refreshToken);
  }
  async logout(token: string) {
    await this.sessions.revoke(token);
    return { message: 'Logged out' };
  }
  async me(id: string) {
    const user = await this.users.findById(id);
    if (!user) throw new UnauthorizedException('Invalid or expired token');
    if (user.status !== 'ACTIVE')
      throw new ForbiddenException('Account is not active');
    return this.publicUser(user);
  }
  private fingerprint(hash: string): string {
    return createHash('sha256').update(hash).digest('hex');
  }
  private tokens(
    user: User,
    sessionId: string,
    refreshToken: string,
  ): AuthResponseDto {
    return {
      access_token: this.jwt.sign(
        {
          sub: String(user.id),
          username: user.username,
          role: user.role,
          roles: [user.role],
          sid: sessionId,
          amr: ['pwd', 'email_otp'],
        },
        { expiresIn: this.settings.accessSeconds },
      ),
      refresh_token: refreshToken,
      expires_in: this.settings.accessSeconds,
      token_type: 'Bearer',
    };
  }
  private publicUser(user: User) {
    return {
      id: String(user.id),
      account_id: user.accountId,
      email: user.email,
      username: user.username,
      name: user.name,
      role: user.role,
      email_verified: user.emailVerified,
    };
  }
}
