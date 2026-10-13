import { Column, Entity, PrimaryColumn } from 'typeorm';
@Entity('auth_sessions')
export class AuthSession {
  @PrimaryColumn('uuid') id: string;
  @Column({ name: 'user_id', type: 'bigint' }) userId: string;
  @Column({ name: 'token_hash', length: 64, unique: true, select: false })
  tokenHash: string;
  @Column({ name: 'expires_at', type: 'timestamptz' }) expiresAt: Date;
  @Column({ default: false }) revoked: boolean;
}
