-- Restore real currency entries. Do not relabel ETF holdings or change balances.
BEGIN;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM instruments WHERE symbol = 'SGOV' AND catalog_visible) THEN
        IF NOT EXISTS (SELECT 1 FROM instruments WHERE symbol = 'CASHGBP' AND NOT catalog_visible) THEN
            RAISE EXCEPTION 'Original GBP cash entry is missing or already visible';
        END IF;
        UPDATE instruments SET catalog_visible = FALSE WHERE symbol = 'SGOV';
        UPDATE instruments SET catalog_visible = TRUE, tradable = FALSE WHERE symbol = 'CASHGBP';
    END IF;
    IF EXISTS (SELECT 1 FROM instruments WHERE symbol = 'BIL' AND catalog_visible) THEN
        IF NOT EXISTS (SELECT 1 FROM instruments WHERE symbol = 'CASHUSD' AND NOT catalog_visible) THEN
            RAISE EXCEPTION 'Original USD cash entry is missing or already visible';
        END IF;
        UPDATE instruments SET catalog_visible = FALSE WHERE symbol = 'BIL';
        UPDATE instruments SET catalog_visible = TRUE, tradable = FALSE WHERE symbol = 'CASHUSD';
    END IF;
END $$;
COMMIT;
