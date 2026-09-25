-- SEAJ OLTP schema - Accounts Table
-- trading accounts and cash balances (users and accounts combined)

CREATE TABLE accounts (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id      VARCHAR(32) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255),
    phone           VARCHAR(20),
    cash_balance    NUMERIC(18, 2) NOT NULL DEFAULT 0 CHECK (cash_balance >= 0),
    status          account_status NOT NULL DEFAULT 'ACTIVE',
    version         INT NOT NULL DEFAULT 0,
    created_on      TIMESTAMP NOT NULL DEFAULT NOW(),
    last_updated    TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_accounts_account_id ON accounts(account_id);
CREATE INDEX idx_accounts_status ON accounts(status);

-- Seed data
INSERT INTO accounts (account_id, name, email, phone, cash_balance, status) VALUES
    ('ACC-0001', 'Alice Johnson', 'alice.johnson@email.com', '+44-1234-567890', 10000.00, 'ACTIVE'),
    ('ACC-0002', 'Brian Osei', 'brian.osei@email.com', '+44-1234-567891', 5000.00, 'ACTIVE'),
    ('ACC-0003', 'Carla Mendes', 'carla.mendes@email.com', '+44-1234-567892', 0.00, 'SUSPENDED'),
    ('ACC-0004', 'Diane Carter', 'diane.carter@email.com', '+44-1234-567893', 25000.00, 'ACTIVE'),
    ('ACC-0005', 'Ethan Brooks', 'ethan.brooks@email.com', '+44-1234-567894', 8000.00, 'ACTIVE');
