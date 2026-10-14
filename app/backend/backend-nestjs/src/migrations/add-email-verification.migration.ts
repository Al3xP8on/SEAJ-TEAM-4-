import type { MigrationInterface, QueryRunner } from 'typeorm';
export class EmailVerification1791849600000 implements MigrationInterface {
  async up(runner: QueryRunner): Promise<void> {
    await runner.query(
      `ALTER TABLE accounts ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE`,
    );
    await runner.query(`CREATE TABLE email_challenges (
      id UUID PRIMARY KEY, purpose VARCHAR(16) NOT NULL CHECK (purpose IN ('REGISTER', 'LOGIN')), email VARCHAR(255) NOT NULL,
      code_hash VARCHAR(64) NOT NULL, payload JSONB, created_at TIMESTAMPTZ NOT NULL, expires_at TIMESTAMPTZ NOT NULL,
      attempts INT NOT NULL DEFAULT 0 CHECK (attempts >= 0), consumed_at TIMESTAMPTZ
    )`);
    await runner.query(
      `CREATE INDEX email_challenges_recipient ON email_challenges(email, purpose, created_at)`,
    );
    // Existing sessions were issued before mandatory email verification.
    await runner.query(`UPDATE auth_sessions SET revoked = TRUE`);
  }
  async down(runner: QueryRunner): Promise<void> {
    await runner.query('DROP TABLE email_challenges');
    await runner.query('ALTER TABLE accounts DROP COLUMN email_verified');
  }
}
