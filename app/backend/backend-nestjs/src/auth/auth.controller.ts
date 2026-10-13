import {
  Body,
  Controller,
  Get,
  HttpCode,
  Post,
  Req,
  UseGuards,
} from '@nestjs/common';
import { AuthService } from './auth.service';
import { LoginDto } from './dto/login.dto';
import { RegisterDto } from './dto/register.dto';
import { VerifyDto } from './dto/verify.dto';
import { RefreshDto } from './dto/refresh.dto';
import { JwtAuthGuard } from './jwt.guard';
import type { AuthenticatedRequest } from './jwt.guard';
@Controller('auth')
export class AuthController {
  constructor(private readonly auth: AuthService) {}
  @Post('register') @HttpCode(202) register(@Body() dto: RegisterDto) {
    return this.auth.register(dto);
  }
  @Post('login') @HttpCode(202) login(@Body() dto: LoginDto) {
    return this.auth.login(dto);
  }
  @Post('register/verify') verifyRegistration(@Body() dto: VerifyDto) {
    return this.auth.verifyRegistration(dto);
  }
  @Post('login/verify') @HttpCode(200) verifyLogin(@Body() dto: VerifyDto) {
    return this.auth.verifyLogin(dto);
  }
  @Post('refresh') @HttpCode(200) refresh(@Body() dto: RefreshDto) {
    return this.auth.refresh(dto.refresh_token);
  }
  @Post('logout') @HttpCode(200) logout(@Body() dto: RefreshDto) {
    return this.auth.logout(dto.refresh_token);
  }
  @Get('validate') @UseGuards(JwtAuthGuard) validate(
    @Req() request: AuthenticatedRequest,
  ) {
    return { valid: true, sub: request.user.sub, role: request.user.role };
  }
  @Get('me') @UseGuards(JwtAuthGuard) me(@Req() request: AuthenticatedRequest) {
    return this.auth.me(request.user.sub);
  }
}
