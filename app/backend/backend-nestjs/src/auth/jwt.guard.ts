import { Injectable, UnauthorizedException, ExecutionContext } from '@nestjs/common';
import { CanActivate } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';

/**
 * JWT Authentication Guard
 * Validates Bearer tokens in Authorization header and attaches user to request
 */
@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(private readonly jwtService: JwtService) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    const request = context.switchToHttp().getRequest();
    const token = this.extractToken(request);

    if (!token) {
      throw new UnauthorizedException('Authorization token is required');
    }

    try {
      const payload = await this.jwtService.verifyAsync(token, {
        secret: process.env.JWT_SECRET || 'changeme',
      });
      request.user = {
        userId: payload.sub,
        username: payload.username,
        roles: payload.roles || [],
      };
      return true;
    } catch (error) {
      throw new UnauthorizedException('Invalid or expired token');
    }
  }

  private extractToken(request: any): string | null {
    const authHeader = request.headers.authorization;
    if (!authHeader) {
      return null;
    }
    const parts = authHeader.split(' ');
    if (parts.length !== 2) {
      return null;
    }
    const [bearer, token] = parts;
    return bearer === 'Bearer' ? token : null;
  }
}
