// Wires the authentication service to environment configuration and the shared PostgreSQL database.
// Registers account, session and email-challenge entities and runs the authentication migrations.
import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AuthModule } from './auth/auth.module';
import { User } from './users/user.entity';
import { EmailChallenge } from './auth/email-challenge.entity';
import { EmailVerification1791849600000 } from './migrations/add-email-verification.migration';
import { AuthSession } from './auth/auth-session.entity';
import { AuthSessions1791763200000 } from './migrations/create-auth-sessions.migration';

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
        ],
        migrationsRun: true,
        synchronize: false,
      }),
    }),
    AuthModule,
  ],
})
export class AppModule {}
