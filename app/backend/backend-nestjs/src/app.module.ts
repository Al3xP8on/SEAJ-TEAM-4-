// Wires the authentication service to environment configuration and the shared PostgreSQL database.
// Registers account, session and email-challenge entities and runs the authentication migrations.
import { AccountSettings1791982800000 } from './migrations/account-settings.migration';
import { EmailChange1791986400000 } from './migrations/email-change.migration';
import { SavedCards1791990000000 } from './migrations/saved-cards.migration';
import { AccountDeletion1791993600000 } from './migrations/account-deletion.migration';
import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AuthModule } from './auth/auth.module';
import { User } from './users/user.entity';
import { EmailChallenge } from './auth/email-challenge.entity';
import { EmailVerification1791849600000 } from './migrations/add-email-verification.migration';
import { AuthSession } from './auth/auth-session.entity';
import { AuthSessions1791763200000 } from './migrations/create-auth-sessions.migration';
import { AccountRoles1791896400000 } from './migrations/align-account-roles.migration';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: ['.env', '../../../.env'],
    }),
    TypeOrmModule.forRootAsync({
      imports: [ConfigModule],
      inject: [ConfigService],
      useFactory: (configService: ConfigService) => ({
        type: 'postgres',
        host: configService.get<string>('DB_HOST') || 'localhost',
        port: parseInt(configService.get<string>('DB_PORT') || '5432', 10),
        username: configService.get<string>('DB_USER') || 'postgres',
        password: configService.getOrThrow<string>('DB_PASSWORD'),
        database: configService.get<string>('DB_NAME') || 'seaj_db',
        entities: [User, AuthSession, EmailChallenge],
        migrations: [
          AuthSessions1791763200000,
          EmailVerification1791849600000,
          AccountRoles1791896400000,
          AccountSettings1791982800000,
          EmailChange1791986400000,
          SavedCards1791990000000,
          AccountDeletion1791993600000,
        ],
        migrationsRun: true,
        synchronize: false,
      }),
    }),
    AuthModule,
  ],
})
export class AppModule {}
