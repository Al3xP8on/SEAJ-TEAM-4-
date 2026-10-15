import { IsString, Matches, Length, IsIn, IsUUID, IsOptional, ValidateNested, IsInt, Min, Max, IsEmail, MaxLength, IsBoolean } from 'class-validator';
import { Type } from 'class-transformer';
export class EmailChangeDto {
  @IsEmail() @MaxLength(255) email: string;
}
export class ProfileDto {
  @IsString() @Matches(/^$|^(?=.{7,20}$)\+?[0-9 ()-]+$/) phone: string;
}
export class BankDto {
  @IsString() @Length(1, 100) holder_name: string;
  @IsString() @Matches(/^[0-9]{13,19}$/) card_number: string;
  @IsInt() @Min(1) @Max(12) expiry_month: number;
  @IsInt() @Min(2026) @Max(2100) expiry_year: number;
}
export class TransferDto {
  @IsIn(['deposit', 'withdraw']) action: 'deposit' | 'withdraw';
  @IsString() @Matches(/^(?:0|[1-9][0-9]{0,6})\.[0-9]{2}$/) amount: string;
  @IsUUID() request_id: string;
  @IsOptional() @IsUUID() card_id?: string;
  @IsOptional() @IsBoolean() save_card?: boolean;
  @IsOptional() @ValidateNested() @Type(() => BankDto) card?: BankDto;
}
