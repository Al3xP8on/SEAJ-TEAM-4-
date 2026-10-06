import { Body, Controller, Post } from '@nestjs/common';
import { AuthService } from './auth.service';

@Controller('auth')
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  @Post('token')
  issueToken(@Body() body: { sub?: string; roles?: string[] }) {
    const sub = body.sub ?? 'stub-user';
    const roles = body.roles ?? ['USER'];
    return { token: this.authService.issueToken(sub, roles) };
  }

  @Post('login')
  async login(@Body() body: { username: string; password: string }) {
    if (!body.username || !body.password) {
      return { error: 'Username and password required' };
    }

    const token = await this.authService.loginWithCredentials(
      body.username,
      body.password,
    );
    return { token };
  }
}
