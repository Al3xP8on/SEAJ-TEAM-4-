import { Injectable, UnauthorizedException } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';

@Injectable()
export class AuthService {
  constructor(private readonly jwt: JwtService) {}

  issueToken(sub: string, roles: string[]): string {
    return this.jwt.sign({ sub, roles });
  }

  // TODO: Integrate with database when ready
  // For now, this is a placeholder for username/password login
  async loginWithCredentials(username: string, password: string): Promise<string> {
    // Placeholder: In production, query database and verify password hash
    if (!username || !password) {
      throw new UnauthorizedException('Invalid credentials');
    }

    // TODO: Replace with actual database lookup and bcrypt password verification
    // const account = await accountsRepository.findOne({ where: { username } });
    // if (!account || !await bcrypt.compare(password, account.password)) {
    //   throw new UnauthorizedException('Invalid credentials');
    // }

    // For now, accept any non-empty credentials
    const token = this.issueToken(username, ['USER']);
    return token;
  }
}
