import type { MigrationInterface, QueryRunner } from 'typeorm';
export class SavedCards1791990000000 implements MigrationInterface {
  async up(runner: QueryRunner): Promise<void> {
    await runner.query("ALTER TABLE accounts ADD COLUMN IF NOT EXISTS saved_cards JSONB NOT NULL DEFAULT '[]'::jsonb");
    await runner.query("UPDATE accounts SET saved_cards = jsonb_build_array(bank_details || jsonb_build_object('id', gen_random_uuid()::text)) WHERE bank_details IS NOT NULL AND saved_cards = '[]'::jsonb");
  }
  async down(runner: QueryRunner): Promise<void> { await runner.query('ALTER TABLE accounts DROP COLUMN saved_cards'); }
}
