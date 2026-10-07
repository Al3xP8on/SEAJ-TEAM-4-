-- SEAJ OLTP schema - Accounts Table
-- trading accounts and cash balances (users and accounts combined)

CREATE TABLE accounts (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id      VARCHAR(32) NOT NULL UNIQUE,
    username        VARCHAR(100) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
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
CREATE INDEX idx_accounts_username ON accounts(username);
CREATE INDEX idx_accounts_status ON accounts(status);

-- Seed data
-- Passwords: alice=SecurePass123!, brian=SecurePass123!, carla=SecurePass123!, diane=SecurePass123!, ethan=SecurePass123!
INSERT INTO accounts (account_id, username, password_hash, name, email, phone, cash_balance, status) VALUES
    ('ACC-0001', 'alice', '$2b$10$FMPKHpY4pIHkK9WkBYOfLOAPIpakQc9eneb8juCGCAVS28gYFTocy', 'Alice Johnson', 'alice.johnson@email.com', '+44-1234-567890', 10000.00, 'ACTIVE'),
    ('ACC-0002', 'brian', '$2b$10$FMPKHpY4pIHkK9WkBYOfLOAPIpakQc9eneb8juCGCAVS28gYFTocy', 'Brian Osei', 'brian.osei@email.com', '+44-1234-567891', 5000.00, 'ACTIVE'),
    ('ACC-0003', 'carla', '$2b$10$FMPKHpY4pIHkK9WkBYOfLOAPIpakQc9eneb8juCGCAVS28gYFTocy', 'Carla Mendes', 'carla.mendes@email.com', '+44-1234-567892', 0.00, 'SUSPENDED'),
    ('ACC-0004', 'diane', '$2b$10$FMPKHpY4pIHkK9WkBYOfLOAPIpakQc9eneb8juCGCAVS28gYFTocy', 'Diane Carter', 'diane.carter@email.com', '+44-1234-567893', 25000.00, 'ACTIVE'),
    ('ACC-0005', 'ethan', '$2b$10$FMPKHpY4pIHkK9WkBYOfLOAPIpakQc9eneb8juCGCAVS28gYFTocy', 'Ethan Brooks', 'ethan.brooks@email.com', '+44-1234-567894', 8000.00, 'ACTIVE');
