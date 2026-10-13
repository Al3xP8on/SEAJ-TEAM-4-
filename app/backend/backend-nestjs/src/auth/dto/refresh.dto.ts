import { IsString, Length } from 'class-validator';
export class RefreshDto {
  @IsString() @Length(1, 256) refresh_token: string;
}
