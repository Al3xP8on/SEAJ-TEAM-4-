import type { MigrationInterface, QueryRunner } from 'typeorm';
export class AccountSettings1791982800000 implements MigrationInterface {
  async up(runner: QueryRunner): Promise<void> {
    await runner.query(`ALTER TABLE accounts ADD COLUMN IF NOT EXISTS bank_details JSONB,
      ADD COLUMN IF NOT EXISTS simulated_bank_balance NUMERIC(18,2) NOT NULL DEFAULT 10000.00,
      ADD COLUMN IF NOT EXISTS cash_transfers JSONB NOT NULL DEFAULT '[]'::jsonb`);
  }
  async down(runner: QueryRunner): Promise<void> {
    await runner.query('ALTER TABLE accounts DROP COLUMN cash_transfers, DROP COLUMN simulated_bank_balance, DROP COLUMN bank_details');
  }
}
