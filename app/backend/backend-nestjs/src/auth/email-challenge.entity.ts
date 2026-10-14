import { Column, Entity, PrimaryColumn } from 'typeorm';
export interface RegistrationPayload {
  username: string;
  email: string;
  name: string;
  passwordHash: string;
  phone?: string | null;
}
export interface LoginPayload {
  userId: string;
  email: string;
  passwordFingerprint: string;
}
@Entity('email_challenges')
export class EmailChallenge {
  @PrimaryColumn('uuid') id: string;
  @Column() purpose: 'REGISTER' | 'LOGIN';
  @Column() email: string;
  @Column({ name: 'code_hash', select: false }) codeHash: string;
  @Column({ type: 'jsonb', select: false, nullable: true }) payload:
    RegistrationPayload | LoginPayload | null;
  @Column({ name: 'created_at', type: 'timestamptz' }) createdAt: Date;
  @Column({ name: 'expires_at', type: 'timestamptz' }) expiresAt: Date;
  @Column({ default: 0 }) attempts: number;
  @Column({ name: 'consumed_at', type: 'timestamptz', nullable: true })
  consumedAt: Date | null;
}
