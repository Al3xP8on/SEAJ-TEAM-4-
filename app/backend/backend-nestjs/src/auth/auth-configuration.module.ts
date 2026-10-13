import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { AuthSettings } from './auth.settings';
@Module({
  imports: [ConfigModule],
  providers: [AuthSettings],
  exports: [AuthSettings],
})
export class AuthConfigurationModule {}
