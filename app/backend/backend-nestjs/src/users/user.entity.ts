// Maps authentication users to the shared accounts table, including verification and account fields.
// Keeps password hashes out of normal queries and serialization while mapping required insert values.
import { Entity, Column, PrimaryGeneratedColumn } from 'typeorm';
import { Exclude } from 'class-transformer';
@Entity('accounts')
export class User {
  @PrimaryGeneratedColumn({ type: 'bigint' }) id: string;
  @Column({ name: 'account_id', length: 32 })
  accountId: string;
  @Column({ unique: true }) username: string;
  @Column({ nullable: true }) email: string;
  @Column({ type: 'varchar', length: 20, nullable: true }) phone: string | null;
  @Exclude() @Column({ name: 'password_hash', select: false }) password: string;
  @Column() name: string;
  @Column({ name: 'cash_balance', type: 'numeric', precision: 18, scale: 2 })
  cashBalance: string;
  @Column({ type: 'integer' }) version: number;
  @Column({ name: 'created_on', type: 'timestamp' }) createdOn: Date;
  @Column({ name: 'last_updated', type: 'timestamp' }) lastUpdated: Date;
  @Column({ default: 'USER' }) roles: string;
  @Column({ default: 'USER' }) role: 'USER' | 'ADMIN';
  @Column({ default: 'ACTIVE' }) status: string;
  @Column({ name: 'email_verified', default: false }) emailVerified: boolean;
}
