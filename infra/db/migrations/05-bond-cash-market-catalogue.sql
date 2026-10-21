-- Bond exposure and cash-equivalent Treasury ETFs use the existing Alpaca stock
-- feed. Category labels describe exposure; instrument names identify the ETFs.
-- Keep legacy orders, holdings, currencies and prices on their original symbols.
BEGIN;
ALTER TABLE instruments ADD COLUMN IF NOT EXISTS catalog_visible BOOLEAN NOT NULL DEFAULT TRUE;
CREATE TEMP TABLE catalogue_counts_before ON COMMIT DROP AS
SELECT asset_class, count(*) AS total FROM instruments WHERE catalog_visible GROUP BY asset_class;

DO $$
DECLARE
    replacement RECORD;
BEGIN
    FOR replacement IN SELECT * FROM (VALUES
        ('CORPB1', 'VCIT', 'Vanguard Intermediate-Term Corporate Bond ETF', 'Bond'),
        ('GILT10', 'VGIT', 'Vanguard Intermediate-Term Treasury ETF', 'Bond'),
        ('UST10', 'GOVT', 'iShares U.S. Treasury Bond ETF', 'Bond'),
        ('UST2', 'VGSH', 'Vanguard Short-Term Treasury ETF', 'Bond'),
        ('UST5', 'SCHR', 'Schwab Intermediate-Term U.S. Treasury ETF', 'Bond'),
        ('UST30', 'VGLT', 'Vanguard Long-Term Treasury ETF', 'Bond'),
        ('CASHGBP', 'SGOV', 'iShares 0-3 Month Treasury Bond ETF (cash equivalent)', 'Cash'),
        ('CASHUSD', 'BIL', 'SPDR Bloomberg 1-3 Month T-Bill ETF (cash equivalent)', 'Cash')
    ) AS replacements(old_symbol, new_symbol, new_name, category)
    LOOP
        IF EXISTS (SELECT 1 FROM instruments WHERE symbol = replacement.old_symbol AND catalog_visible) THEN
            IF EXISTS (SELECT 1 FROM instruments WHERE symbol = replacement.new_symbol) THEN
                RAISE EXCEPTION 'Replacement % already exists; catalogue unchanged', replacement.new_symbol;
            END IF;
            IF NOT EXISTS (SELECT 1 FROM instruments WHERE symbol = replacement.old_symbol AND asset_class = replacement.category) THEN
                RAISE EXCEPTION 'Unexpected asset class for %', replacement.old_symbol;
            END IF;
            INSERT INTO instruments (symbol, name, asset_class, currency, tradable, catalog_visible)
            VALUES (replacement.new_symbol, replacement.new_name, replacement.category, 'USD', TRUE, TRUE);
            UPDATE instruments SET catalog_visible = FALSE, tradable = FALSE WHERE symbol = replacement.old_symbol;
        END IF;
    END LOOP;
    IF EXISTS (
        (SELECT asset_class, count(*) FROM instruments WHERE catalog_visible GROUP BY asset_class
         EXCEPT SELECT asset_class, total FROM catalogue_counts_before)
        UNION ALL
        (SELECT asset_class, total FROM catalogue_counts_before
         EXCEPT SELECT asset_class, count(*) FROM instruments WHERE catalog_visible GROUP BY asset_class)
    ) THEN
        RAISE EXCEPTION 'Catalogue counts changed; rolling back';
    END IF;
END $$;
COMMIT;
