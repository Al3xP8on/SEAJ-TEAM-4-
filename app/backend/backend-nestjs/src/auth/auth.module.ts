import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { JwtModule } from '@nestjs/jwt';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';
import { JwtAuthGuard } from './jwt.guard';
import { AuthConfigurationModule } from './auth-configuration.module';
import { AuthSettings } from './auth.settings';
import { SessionService } from './session.service';
import { EmailChallenge } from './email-challenge.entity';
import { EmailChallengeService } from './email-challenge.service';
import { EmailService } from './email.service';
import { AuthSession } from './auth-session.entity';
import { UsersModule } from '../users/users.module';
@Module({
  imports: [
    ConfigModule,
    AuthConfigurationModule,
    UsersModule,
    TypeOrmModule.forFeature([AuthSession, EmailChallenge]),
    JwtModule.registerAsync({
      imports: [AuthConfigurationModule],
      inject: [AuthSettings],
      useFactory: (settings: AuthSettings) => ({
        secret: settings.secret,
        signOptions: { algorithm: 'HS256', expiresIn: settings.accessSeconds },
      }),
    }),
  ],
  controllers: [AuthController],
  providers: [
    AuthService,
    JwtAuthGuard,
    SessionService,
    EmailChallengeService,
    EmailService,
  ],
  exports: [JwtModule, JwtAuthGuard],
})
export class AuthModule {}
