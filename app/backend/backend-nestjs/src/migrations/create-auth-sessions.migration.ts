import type { MigrationInterface, QueryRunner } from 'typeorm';
export class AuthSessions1791763200000 implements MigrationInterface {
  async up(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query(
      `ALTER TABLE accounts ADD COLUMN IF NOT EXISTS role VARCHAR(16) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN'))`,
    );
    await queryRunner.query(
      `CREATE UNIQUE INDEX IF NOT EXISTS accounts_email_lower_unique ON accounts (LOWER(email)) WHERE email IS NOT NULL`,
    );
    await queryRunner.query(`CREATE TABLE IF NOT EXISTS auth_sessions (
      id UUID PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
      token_hash VARCHAR(64) NOT NULL UNIQUE, expires_at TIMESTAMPTZ NOT NULL, revoked BOOLEAN NOT NULL DEFAULT FALSE
    )`);
    await queryRunner.query(
      `CREATE INDEX IF NOT EXISTS auth_sessions_user_id ON auth_sessions(user_id)`,
    );
  }
  async down(queryRunner: QueryRunner): Promise<void> {
    await queryRunner.query('DROP TABLE auth_sessions');
    await queryRunner.query('DROP INDEX accounts_email_lower_unique');
    await queryRunner.query('ALTER TABLE accounts DROP COLUMN role');
  }
}
