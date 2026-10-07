import { Entity, Column, PrimaryGeneratedColumn, CreateDateColumn, UpdateDateColumn } from 'typeorm';

@Entity('accounts')
export class User {
  @PrimaryGeneratedColumn()
  id: number;

  @Column({ unique: true })
  username: string;

  @Column({ nullable: true })
  email: string;

  @Column({ name: 'password_hash' })
  password: string;

  @Column()
  name: string;

  @CreateDateColumn({ name: 'created_on' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'last_updated' })
  updatedAt: Date;

  @Column({ default: 0 })
  version: number;
}