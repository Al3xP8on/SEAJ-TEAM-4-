import {
  IsEmail,
  IsString,
  Length,
  MaxLength,
  ValidateIf,
  Validate,
} from 'class-validator';
import { PasswordBytes } from './login.dto';
export class RegisterDto {
  @IsEmail() @MaxLength(255) email: string;
  @IsString() @Length(8, 72) @Validate(PasswordBytes) password: string;
  @IsString()
  @Length(3, 100)
  username: string;
  @ValidateIf((_object, value) => value !== undefined)
  @IsString()
  @Length(1, 255)
  name?: string;
}
