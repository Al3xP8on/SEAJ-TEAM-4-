import type { MigrationInterface, QueryRunner } from 'typeorm';
export class EmailChange1791986400000 implements MigrationInterface {
  async up(runner: QueryRunner): Promise<void> {
    await runner.query("ALTER TABLE email_challenges DROP CONSTRAINT IF EXISTS email_challenges_purpose_check");
    await runner.query("ALTER TABLE email_challenges ADD CONSTRAINT email_challenges_purpose_check CHECK (purpose IN ('REGISTER', 'LOGIN', 'EMAIL_CHANGE'))");
  }
  async down(runner: QueryRunner): Promise<void> {
    await runner.query("DELETE FROM email_challenges WHERE purpose = 'EMAIL_CHANGE'");
    await runner.query("ALTER TABLE email_challenges DROP CONSTRAINT email_challenges_purpose_check");
    await runner.query("ALTER TABLE email_challenges ADD CONSTRAINT email_challenges_purpose_check CHECK (purpose IN ('REGISTER', 'LOGIN'))");
  }
}
