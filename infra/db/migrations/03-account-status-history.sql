CREATE TABLE IF NOT EXISTS account_status_history (
    id              BIGSERIAL PRIMARY KEY,
    account_id      BIGINT NOT NULL REFERENCES accounts(id),
    admin_id        BIGINT NOT NULL REFERENCES accounts(id),
    previous_status account_status NOT NULL,
    new_status      account_status NOT NULL,
    reason          VARCHAR(500) NOT NULL,
    changed_on      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_account_status_history_account
    ON account_status_history(account_id, changed_on DESC);
