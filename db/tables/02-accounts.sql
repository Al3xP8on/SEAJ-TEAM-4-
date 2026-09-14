-- SEAJ OLTP schema - Accounts Table
-- trading accounts and cash balances (references clients table)

CREATE TABLE accounts (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id       BIGINT NOT NULL REFERENCES clients(id),
    account_id      VARCHAR(32) NOT NULL UNIQUE,
    cash_balance    NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (cash_balance >= 0),
    status          account_status NOT NULL DEFAULT 'ACTIVE',
    version         INT NOT NULL DEFAULT 0,
    last_updated    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_accounts_client_id ON accounts(client_id);
CREATE INDEX idx_accounts_account_id ON accounts(account_id);
CREATE INDEX idx_accounts_status ON accounts(status);

-- Seed data
INSERT INTO accounts (client_id, account_id, cash_balance, status) VALUES
    (1, 'ACC-0001', 10000.00, 'ACTIVE'),
    (2, 'ACC-0002', 5000.00, 'ACTIVE'),
    (3, 'ACC-0003', 0.00, 'SUSPENDED'),
    (4, 'ACC-0004', 25000.00, 'ACTIVE'),
    (5, 'ACC-0005', 8000.00, 'ACTIVE');
