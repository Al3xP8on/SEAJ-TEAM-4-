import type { MigrationInterface, QueryRunner } from 'typeorm';
export class AccountDeletion1791993600000 implements MigrationInterface {
  async up(runner: QueryRunner): Promise<void> {
    await runner.query('ALTER TABLE accounts DROP COLUMN IF EXISTS simulated_bank_balance');
    await runner.query('ALTER TABLE email_challenges DROP CONSTRAINT IF EXISTS email_challenges_purpose_check');
    await runner.query("ALTER TABLE email_challenges ADD CONSTRAINT email_challenges_purpose_check CHECK (purpose IN ('REGISTER', 'LOGIN', 'EMAIL_CHANGE', 'DELETE_ACCOUNT'))");
  }
  async down(runner: QueryRunner): Promise<void> {
    await runner.query('ALTER TABLE accounts ADD COLUMN simulated_bank_balance NUMERIC(18,2) NOT NULL DEFAULT 10000.00');
    await runner.query("DELETE FROM email_challenges WHERE purpose = 'DELETE_ACCOUNT'");
    await runner.query('ALTER TABLE email_challenges DROP CONSTRAINT email_challenges_purpose_check');
    await runner.query("ALTER TABLE email_challenges ADD CONSTRAINT email_challenges_purpose_check CHECK (purpose IN ('REGISTER', 'LOGIN', 'EMAIL_CHANGE'))");
  }
}
