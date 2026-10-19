import {
  IsEmail,
  IsString,
  Length,
  Matches,
  MaxLength,
  ValidateIf,
  Validate,
  IsIn,
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
  // Optional contact number stored on the account; not used for verification
  @ValidateIf((_object, value) => value !== undefined)
  @IsString()
  @Matches(/^(?=.{7,20}$)\+?[0-9 ()-]+$/, {
    message: 'phone must be 7-20 characters: digits, spaces, dashes or brackets, with an optional leading +',
  })
  phone?: string;
  @ValidateIf((_object, value) => value !== undefined)
  @IsIn(['USER', 'ADMIN'])
  role?: 'USER' | 'ADMIN';
}
