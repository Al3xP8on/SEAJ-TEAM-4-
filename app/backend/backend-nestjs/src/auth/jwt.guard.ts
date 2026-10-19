import {
  Injectable,
  UnauthorizedException,
  ExecutionContext,
  CanActivate,
} from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import type { Request } from 'express';
import { SessionService } from './session.service';
import { UsersService } from '../users/users.service';

export interface AuthenticatedRequest extends Request {
  user: { sub: string; role: 'USER' | 'ADMIN'; sid: string };
}
@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(
    private readonly jwt: JwtService,
    private readonly sessions: SessionService,
    private readonly users: UsersService,
  ) {}
  async canActivate(context: ExecutionContext): Promise<boolean> {
    const request = context.switchToHttp().getRequest<AuthenticatedRequest>();
    const match = /^Bearer ([^ ]+)$/i.exec(request.headers.authorization ?? '');
    if (!match)
      throw new UnauthorizedException('Authorization token is required');
    try {
      const payload = await this.jwt.verifyAsync<{
        sub: string;
        role: 'USER' | 'ADMIN';
        sid: string;
        exp: number;
        amr: string[];
      }>(match[1], { algorithms: ['HS256'] });
      if (
        typeof payload.sub !== 'string' ||
        typeof payload.sid !== 'string' ||
        !Number.isFinite(payload.exp) ||
        !Array.isArray(payload.amr) ||
        !payload.amr.includes('pwd') ||
        !payload.amr.includes('email_otp') ||
        !['USER', 'ADMIN'].includes(payload.role) ||
        !(await this.sessions.isActive(payload.sid, payload.sub))
      )
        throw new Error('Invalid session');
      const account = await this.users.findById(payload.sub);
      if (!account || account.status !== 'ACTIVE')
        throw new Error('Account is unavailable');
      request.user = payload;
      return true;
    } catch {
      throw new UnauthorizedException('Invalid or expired token');
    }
  }
}
