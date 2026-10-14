import {
  IsString,
  Length,
  Validate,
  ValidatorConstraint,
  ValidatorConstraintInterface,
} from 'class-validator';
@ValidatorConstraint({ name: 'passwordBytes' })
export class PasswordBytes implements ValidatorConstraintInterface {
  validate(value: unknown): boolean {
    return typeof value === 'string' && Buffer.byteLength(value, 'utf8') <= 72;
  }
  defaultMessage(): string {
    return 'password must not exceed 72 UTF-8 bytes';
  }
}
export class LoginDto {
  @IsString() @Length(3, 100) username: string;
  @IsString() @Length(1, 72) @Validate(PasswordBytes) password: string;
}
