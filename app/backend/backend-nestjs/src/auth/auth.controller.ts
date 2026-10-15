import {
  Body,
  Controller,
  Get,
  HttpCode,
  Post,
  Req,
  UseGuards,
} from '@nestjs/common';
import { DeleteAccountDto, RemoveCardDto } from './dto/delete-account.dto';
import { ProfileDto, BankDto, TransferDto, EmailChangeDto } from './dto/account-settings.dto';
import { AuthService } from './auth.service';
import { ChangePasswordDto } from './dto/change-password.dto';
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
  @Post('email') @HttpCode(202) @UseGuards(JwtAuthGuard)
  changeEmail(@Req() request: AuthenticatedRequest, @Body() dto: EmailChangeDto) { return this.auth.changeEmail(request.user.sub, dto.email); }
  @Post('email/verify') @HttpCode(200) @UseGuards(JwtAuthGuard)
  verifyEmail(@Req() request: AuthenticatedRequest, @Body() dto: VerifyDto) { return this.auth.verifyEmailChange(request.user.sub, dto); }
  @Get('settings') @UseGuards(JwtAuthGuard)
  settings(@Req() request: AuthenticatedRequest) { return this.auth.accountSettings(request.user.sub); }
  @Post('profile') @HttpCode(200) @UseGuards(JwtAuthGuard)
  profile(@Req() request: AuthenticatedRequest, @Body() dto: ProfileDto) { return this.auth.updatePhone(request.user.sub, dto.phone); }
  @Post('bank') @HttpCode(200) @UseGuards(JwtAuthGuard)
  bank(@Req() request: AuthenticatedRequest, @Body() dto: BankDto) { return this.auth.saveBank(request.user.sub, dto); }
  @Post('cards/remove') @HttpCode(200) @UseGuards(JwtAuthGuard)
  removeCard(@Req() request: AuthenticatedRequest, @Body() dto: RemoveCardDto) { return this.auth.removeCard(request.user.sub, dto.card_id); }
  @Post('account/delete/start') @HttpCode(202) @UseGuards(JwtAuthGuard)
  startDeletion(@Req() request: AuthenticatedRequest) { return this.auth.startDeletion(request.user.sub); }
  @Post('account/delete/verify') @HttpCode(200) @UseGuards(JwtAuthGuard)
  verifyDeletion(@Req() request: AuthenticatedRequest, @Body() dto: VerifyDto) { return this.auth.verifyDeletion(request.user.sub, request.user.sid, dto); }
  @Post('account/delete/confirm') @HttpCode(200) @UseGuards(JwtAuthGuard)
  confirmDeletion(@Req() request: AuthenticatedRequest, @Body() dto: DeleteAccountDto) { return this.auth.confirmDeletion(request.user.sub, request.user.sid, dto.deletion_token); }
  @Post('transfers') @HttpCode(200) @UseGuards(JwtAuthGuard)
  transfer(@Req() request: AuthenticatedRequest, @Body() dto: TransferDto) { return this.auth.transfer(request.user.sub, dto); }
  @Post('password') @HttpCode(200) @UseGuards(JwtAuthGuard)
  changePassword(@Req() request: AuthenticatedRequest, @Body() dto: ChangePasswordDto) {
    return this.auth.changePassword(request.user.sub, dto);
  }
  @Get('me') @UseGuards(JwtAuthGuard) me(@Req() request: AuthenticatedRequest) {
    return this.auth.me(request.user.sub);
  }
}
