import { Body, Controller, Post } from '@nestjs/common';
import { AuthService } from './auth.service';
import { LoginDto } from './dto/login.dto';
import { AuthResponseDto } from './dto/auth.dto';

@Controller('auth')
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  @Post('login')
  async login(
    @Body() loginDto: LoginDto,
  ): Promise<AuthResponseDto> {
    return this.authService.login(loginDto);
  }

  @Post('token')
  issueToken(@Body() body: { sub?: string; roles?: string[] }) {
    const sub = body.sub ?? 'stub-user';
    const roles = body.roles ?? ['USER'];
    return { token: this.authService.issueToken(sub, roles) };
  }
}
