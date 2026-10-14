// Adds the merged Java roles column only to databases created before that schema change.
// Preserves existing role lists and copies legacy primary roles without changing account identifiers.
import type { MigrationInterface, QueryRunner } from 'typeorm';
export class AccountRoles1791896400000 implements MigrationInterface {
  async up(runner: QueryRunner): Promise<void> {
    if (!(await runner.hasColumn('accounts', 'roles'))) {
      await runner.query(`ALTER TABLE accounts ADD COLUMN roles VARCHAR(255) NOT NULL DEFAULT 'USER'`);
      await runner.query(`UPDATE accounts SET roles = role`);
    }
  }
  async down(): Promise<void> {
    // Shared Java data must remain: the column can predate this migration entirely.
  }
}
