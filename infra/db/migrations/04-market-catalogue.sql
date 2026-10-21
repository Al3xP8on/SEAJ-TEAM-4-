-- Replace unsupported sample equity in the browsable catalogue, preserving its
-- original identity, currency, prices, orders and positions for historical records.
-- Re-runnable. No portfolio balances, quantities or order symbols are rewritten.
BEGIN;
ALTER TABLE instruments ADD COLUMN IF NOT EXISTS catalog_visible BOOLEAN NOT NULL DEFAULT TRUE;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM instruments WHERE symbol = 'GLBEQ1' AND catalog_visible) THEN
        IF EXISTS (SELECT 1 FROM instruments WHERE symbol = 'IBM') THEN
            RAISE EXCEPTION 'IBM already exists; cannot replace GLBEQ1 without changing catalogue counts';
        END IF;
        INSERT INTO instruments (symbol, name, asset_class, currency, tradable, catalog_visible)
        VALUES ('IBM', 'International Business Machines Corporation', 'Equity', 'USD', TRUE, TRUE);
        UPDATE instruments SET catalog_visible = FALSE, tradable = FALSE WHERE symbol = 'GLBEQ1';
    END IF;
END $$;
COMMIT;
