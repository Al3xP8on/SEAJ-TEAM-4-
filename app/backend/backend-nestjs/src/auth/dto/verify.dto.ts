import { IsUUID, IsString, Matches } from 'class-validator';
export class VerifyDto {
  @IsUUID('4') challenge_id: string;
  @IsString() @Matches(/^[0-9]{6}$/) code: string;
}
