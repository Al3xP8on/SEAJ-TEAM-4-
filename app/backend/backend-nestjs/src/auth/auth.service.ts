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
import { ChangePasswordDto } from './dto/change-password.dto';
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
  async changeEmail(id: string, email: string) {
    const user = await this.users.findById(id);
    if (!user || user.status !== 'ACTIVE') throw new UnauthorizedException();
    return this.challenges.issue('EMAIL_CHANGE', email.toLowerCase(), { userId: id, email: email.toLowerCase() });
  }
  verifyEmailChange(id: string, dto: VerifyDto) {
    return this.challenges.verify(dto, 'EMAIL_CHANGE', async (challenge, manager) => {
      const payload = challenge.payload as { userId: string; email: string };
      if (payload.userId !== id) throw new UnauthorizedException();
      return this.users.updateEmail(id, payload.email, manager);
    });
  }
  async startDeletion(id: string) {
    const user = await this.users.deletionAccount(id);
    return this.challenges.issue('DELETE_ACCOUNT', user.email, { userId: id, email: user.email });
  }
  verifyDeletion(id: string, sid: string, dto: VerifyDto) {
    return this.challenges.verify(dto, 'DELETE_ACCOUNT', async (challenge, manager) => {
      const payload = challenge.payload as { userId: string; email: string };
      if (payload.userId !== id) throw new UnauthorizedException('Invalid verification code');
      const user = await this.users.deletionAccount(id, manager);
      if (user.email !== payload.email) throw new UnauthorizedException('Email changed. Request a new deletion code');
      return { deletion_token: this.jwt.sign({ sub: id, sid, purpose: 'DELETE_ACCOUNT', email: user.email }, { expiresIn: 300 }), expires_in: 300 };
    });
  }
  async confirmDeletion(id: string, sid: string, token: string) {
    let grant: { sub: string; sid: string; purpose: string; email: string; exp: number };
    try { grant = await this.jwt.verifyAsync(token, { algorithms: ['HS256'] }); }
    catch { throw new UnauthorizedException('Deletion approval expired. Request a new code'); }
    if (grant.sub !== id || grant.sid !== sid || grant.purpose !== 'DELETE_ACCOUNT' || !Number.isFinite(grant.exp) || typeof grant.email !== 'string') {
      throw new UnauthorizedException('Invalid deletion approval');
    }
    return this.users.deleteAccount(id, grant.email);
  }
  removeCard(id: string, cardId: string) { return this.users.removeCard(id, cardId); }
  accountSettings(id: string) { return this.users.settings(id); }
  updatePhone(id: string, phone: string) { return this.users.updatePhone(id, phone); }
  saveBank(id: string, bank: import('./dto/account-settings.dto').BankDto) { return this.users.saveBank(id, bank); }
  transfer(id: string, dto: import('./dto/account-settings.dto').TransferDto) { return this.users.transfer(id, dto); }
  async changePassword(id: string, dto: ChangePasswordDto) {
    await this.users.changePassword(id, dto.current_password, dto.new_password);
    return { message: 'Password changed successfully' };
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
  private accountRoles(user: User): string[] {
    const roles = user.roles?.split(',').map(role => role.trim().toUpperCase()).filter(Boolean);
    return roles?.length ? roles : [user.role];
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
          roles: this.accountRoles(user),
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
