import { ApiProperty } from '@nestjs/swagger';
import { IsNotEmpty, MinLength } from 'class-validator';

export class LoginDto {
  @ApiProperty({
    example: 'alice',
    description: 'Username',
  })
  @IsNotEmpty()
  username: string;

  @ApiProperty({
    example: 'SecurePass123!',
    description: 'User password (minimum 8 characters)',
  })
  @IsNotEmpty()
  @MinLength(8)
  password: string;
}