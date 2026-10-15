import { IsString, Length, Validate } from 'class-validator';
import { PasswordBytes } from './login.dto';
export class ChangePasswordDto {
  @IsString() @Length(1, 72) @Validate(PasswordBytes) current_password: string;
  @IsString() @Length(8, 72) @Validate(PasswordBytes) new_password: string;
}
