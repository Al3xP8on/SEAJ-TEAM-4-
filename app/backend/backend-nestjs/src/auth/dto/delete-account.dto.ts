import { IsString, Length, IsUUID } from 'class-validator';
export class DeleteAccountDto { @IsString() @Length(1, 2048) deletion_token: string; }
export class RemoveCardDto { @IsUUID() card_id: string; }
